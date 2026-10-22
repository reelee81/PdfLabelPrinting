package hu.reelee81.pdflabelprinting.pdf

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import hu.reelee81.pdflabelprinting.core.NupConfig
import hu.reelee81.pdflabelprinting.core.PageGroups
import hu.reelee81.pdflabelprinting.core.PageSizes
import hu.reelee81.pdflabelprinting.core.PdfCropRect
import hu.reelee81.pdflabelprinting.core.PdfCrops
import hu.reelee81.pdflabelprinting.core.PdfOperationResult
import hu.reelee81.pdflabelprinting.core.ProgressMath
import hu.reelee81.pdflabelprinting.core.ProgressNotifyPacing
import hu.reelee81.pdflabelprinting.core.ProgressTicker
import hu.reelee81.pdflabelprinting.core.SourcePageArrays
import hu.reelee81.pdflabelprinting.core.SourcePageRuns
import hu.reelee81.pdflabelprinting.core.WorkFileBackup
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfLayoutChangeOperation(
    private val files: Files,
    private val saveSelection: () -> Unit,
    private val input: Input,
    private val progress: Progress,
    private val warn: (message: String, error: Throwable) -> Unit = { _, _ -> }
) {
    class Files(val workDir: File, val source: File, val plp: File, val backup: WorkFileBackup, val sourceWork: File)

    sealed interface Change {
        class Crop(val plpIndex: Int, val crop: PdfCropRect?, val applyToAll: Boolean) : Change
        class Rotate(val plpIndices: List<Int>) : Change
        class Position(val plpIndices: List<Int>, val position: Int) : Change
        class Rebuild(val oldPerGroup: Int, val resetPositions: Boolean = false) : Change
        class DeleteGroups(val plpIndices: List<Int>) : Change
        data object AddEmptyPage : Change
        class Reorder(val groupOrder: List<Int>) : Change
    }

    class Input(
        val change: Change,
        val perGroup: Int,
        val config: NupConfig,
        val crops: Map<Int, PdfCropRect>,
        val rotation: IntArray,
        val position: IntArray,
        val defaultPosition: Int,
        val batchSize: Int,
        val selected: List<Int>
    )

    class Progress(val clock: () -> Long, val tickMs: Long, val idleMs: Long)

    class Result(
        val crops: Map<Int, PdfCropRect>,
        val rotation: IntArray,
        val position: IntArray,
        val defaultPosition: Int,
        val config: NupConfig?,
        val selected: List<Int>,
        val progress: ProgressTracker.Snapshot,
        override val pageSizes: PageSizes?,
        val cleared: Boolean = false
    ) : PdfOperationResult

    @Volatile private var backedUp = false

    private val rewritesSource: Boolean
        get() = input.change is Change.DeleteGroups || input.change is Change.AddEmptyPage || input.change is Change.Reorder

    suspend fun execute(report: (Int?) -> Unit): Result? = withContext(Dispatchers.IO) {
        val tracker = ProgressTracker(progress.clock)
        tracker.reset()

        val built = ProgressTicker.run(
            tick = progress.tickMs.milliseconds,
            percent = {
                val now = progress.clock()
                if (now - tracker.startedAt < ProgressNotifyPacing.REVEAL_MS) null
                else tracker.percent(now, progress.idleMs)
            },
            report = report,
            onCancel = tracker::cancel
        ) {
            saveSelection()

            val total = sourcePageCount()
            if (total <= 0 && input.change !is Change.Rebuild) return@run null

            val removed = (input.change as? Change.DeleteGroups)
                ?.let { SourcePageRuns.removedPages(it.plpIndices, input.perGroup, total) }
            if (removed != null && total - removed.size <= 0) {
                return@run Built(emptyMap(), IntArray(0), IntArray(0), null, emptyList(), cleared = true)
            }

            files.backup.backup()
            backedUp = true

            try {
                val newTotal = rewriteSource(total, removed, tracker::stage)
                val (crops, rotation, position) = changedArrays(total, newTotal, removed)

                val work = File(files.workDir, "${files.plp.name}.part")
                if (work.exists()) work.delete()
                PdfNupBuilder.build(
                    source = files.source,
                    totalSrcPages = newTotal,
                    outFile = work,
                    workDir = files.workDir,
                    config = input.config,
                    rotation = rotation,
                    position = position,
                    crop = crops,
                    batchSize = input.batchSize,
                    progressStage = tracker::stage,
                    warn = warn
                )
                if (files.plp.exists()) files.plp.delete()
                work.renameTo(files.plp)

                val pageSizes = PdfPageSizes.forThumbnails(files.source, newTotal, input.config, input.batchSize, tracker::stage)

                Built(crops, rotation, position, pageSizes, selectedAfter(total))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (rewritesSource) {
                    files.backup.restore()
                    backedUp = false
                }
                throw e
            }
        } ?: return@withContext null

        Result(built.crops, built.rotation, built.position, input.defaultPosition, input.config, built.selected, tracker.snapshot(), built.pageSizes, built.cleared)
    }

    private fun rewriteSource(total: Int, removed: Set<Int>?, stage: (Long) -> PdfProgress): Int {
        val per = (input.config.rows * input.config.cols).coerceAtLeast(1)
        return when (val change = input.change) {
            is Change.DeleteGroups -> {
                val remaining = total - removed!!.size
                write(SourcePageRuns.kept(total, removed), stage(remaining + ProgressMath.layoutUnits(remaining, per)))
                remaining
            }
            Change.AddEmptyPage -> {
                val firstPageSize = PdfDocument(PdfIo.reader(files.source.absolutePath)).use { src ->
                    val p = src.getPage(1).pageSize
                    PageSize(p.width, p.height)
                }
                write(listOf(1..total), stage(total + ProgressMath.layoutUnits(total + 1, per)), firstPageSize)
                total + 1
            }
            is Change.Reorder -> {
                write(SourcePageRuns.reordered(change.groupOrder, input.perGroup, total), stage(total + ProgressMath.layoutUnits(total, per)))
                total
            }
            else -> total
        }
    }

    private fun write(runs: List<IntRange>, progress: PdfProgress, emptyPage: PageSize? = null) {
        PdfSourceRewrite.write(files.source, files.sourceWork, runs, input.batchSize, progress, emptyPage)
    }

    private fun selectedAfter(total: Int): List<Int> = when (val change = input.change) {
        is Change.Rebuild -> regrouped(change.oldPerGroup, total)
        Change.AddEmptyPage -> regrouped(input.perGroup, total)
        is Change.DeleteGroups -> {
            val deleted = change.plpIndices.toSet()
            input.selected.filter { it !in deleted }.map { index -> index - deleted.count { it < index } }
        }
        else -> input.selected
    }

    private fun regrouped(oldPerGroup: Int, total: Int): List<Int> {
        if (input.selected.isEmpty() || total <= 0) return emptyList()
        val newPer = (input.config.rows.coerceAtLeast(1)) * (input.config.cols.coerceAtLeast(1))
        val selectedPages = PageGroups.sourcePagesOfGroups(input.selected, oldPerGroup, total)
        return PageGroups.groupsOfSourcePages(selectedPages, newPer).toList()
    }

    private fun changedArrays(total: Int, newTotal: Int, removed: Set<Int>?): Triple<Map<Int, PdfCropRect>, IntArray, IntArray> {
        val per = input.perGroup
        val rotation = SourcePageArrays.rotationsSized(input.rotation, total)
        val position = SourcePageArrays.positionsSized(input.position, total, input.defaultPosition)
        return when (val change = input.change) {
            is Change.Crop -> {
                val groupStart = change.plpIndex * per
                val indices = if (change.applyToAll) 0 until total else groupStart until min(total, groupStart + per)
                Triple(PdfCrops.withCrop(input.crops.filterKeys { it < total }, indices, change.crop), rotation, position)
            }
            is Change.Rotate ->
                Triple(input.crops, SourcePageArrays.withRotatedGroups(rotation, change.plpIndices, per, total), position)
            is Change.Position ->
                Triple(input.crops, rotation, SourcePageArrays.withGroupPosition(position, change.plpIndices, per, total, change.position))
            is Change.Rebuild ->
                Triple(input.crops, rotation, if (change.resetPositions) IntArray(total) { input.defaultPosition } else position)
            is Change.DeleteGroups ->
                Triple(
                    PdfCrops.afterRemoval(input.crops, removed!!),
                    SourcePageArrays.afterRemoval(rotation, removed),
                    SourcePageArrays.afterRemoval(position, removed)
                )
            Change.AddEmptyPage ->
                Triple(
                    input.crops,
                    SourcePageArrays.rotationsSized(SourcePageArrays.rotationsGrown(input.rotation, newTotal), newTotal),
                    SourcePageArrays.positionsSized(input.position, newTotal, input.defaultPosition)
                )
            is Change.Reorder -> {
                val newToOld = SourcePageArrays.reorderedIndices(change.groupOrder, per, total)
                Triple(
                    PdfCrops.afterReorder(input.crops, newToOld),
                    SourcePageArrays.rotationsAfterReorder(input.rotation, newToOld),
                    SourcePageArrays.positionsAfterReorder(input.position, newToOld, input.defaultPosition)
                )
            }
        }
    }

    private class Built(
        val crops: Map<Int, PdfCropRect>,
        val rotation: IntArray,
        val position: IntArray,
        val pageSizes: PageSizes?,
        val selected: List<Int>,
        val cleared: Boolean = false
    )

    suspend fun restoreIfBackedUp() {
        if (!backedUp) return
        withContext(Dispatchers.IO) {
            files.backup.restore()
        }
    }

    private fun sourcePageCount(): Int {
        if (input.change is Change.Rebuild) return PdfDocument(PdfIo.reader(files.source.absolutePath)).use { it.numberOfPages }
        if (!files.source.exists()) return 0
        return try { PdfDocument(PdfIo.reader(files.source.absolutePath)).use { it.numberOfPages } } catch (_: Exception) { 0 }
    }
}
