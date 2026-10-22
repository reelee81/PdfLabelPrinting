package hu.reelee81.pdflabelprinting.pdf

import com.itextpdf.kernel.pdf.PdfDocument
import hu.reelee81.pdflabelprinting.core.NupConfig
import hu.reelee81.pdflabelprinting.core.PdfCropRect
import hu.reelee81.pdflabelprinting.core.ProgressMath
import hu.reelee81.pdflabelprinting.core.ProgressNotifyPacing
import hu.reelee81.pdflabelprinting.core.ProgressTicker
import hu.reelee81.pdflabelprinting.core.SourcePageArrays
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PdfViewerEditOperation(
    private val files: PdfLayoutChangeOperation.Files,
    private val saveSelection: () -> Unit,
    private val input: Input,
    private val progress: PdfLayoutChangeOperation.Progress,
    private val copyEdited: (File) -> Unit,
    private val missingSourceMessage: String,
    private val warn: (message: String, error: Throwable) -> Unit = { _, _ -> }
) {
    class Input(
        val config: NupConfig,
        val builtConfig: NupConfig?,
        val crops: Map<Int, PdfCropRect>,
        val rotation: IntArray,
        val position: IntArray,
        val defaultPosition: Int,
        val batchSize: Int,
        val selected: List<Int>
    )

    @Volatile private var backedUp = false

    suspend fun execute(report: (Int?) -> Unit): PdfLayoutChangeOperation.Result = withContext(Dispatchers.IO) {
        val tracker = ProgressTracker(progress.clock)
        tracker.reset()
        val job = coroutineContext[Job]

        val work = File(files.workDir, "${files.plp.name}.edited")
        val projectedSource = File(files.workDir, "${files.source.name}.edited")
        try {
            ProgressTicker.run(
                tick = progress.tickMs.milliseconds,
                percent = {
                    val now = progress.clock()
                    if (now - tracker.startedAt < ProgressNotifyPacing.REVEAL_MS) null
                    else tracker.percent(now, progress.idleMs)
                },
                report = report,
                onCancel = tracker::cancel
            ) {
                files.backup.backup()
                backedUp = true
                saveSelection()

                try {
                    if (work.exists()) work.delete()
                    if (projectedSource.exists()) projectedSource.delete()

                    copyEdited(work)

                    val editedPages = PdfDocument(PdfIo.reader(work.absolutePath)).use { it.numberOfPages }
                    val sourcePages = sourcePageCount()
                    val per = (input.config.rows * input.config.cols).coerceAtLeast(1)
                    val stage = tracker.stage(editedPages + sourcePages + ProgressMath.layoutUnits(sourcePages, per))

                    val projected = try {
                        PdfEditProjection.project(
                            source = files.source,
                            editedPlp = work,
                            output = projectedSource,
                            batchSize = input.batchSize,
                            onProgress = stage::advance
                        ) { job?.ensureActive() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: PdfEditProjection.NotProjectableException) {
                        -1
                    }
                    stage.idle()

                    if (projected > 0) {
                        if (files.source.exists()) files.source.delete()
                        if (!projectedSource.renameTo(files.source)) throw IOException(missingSourceMessage)
                        rebuilt(tracker)
                    } else {
                        if (projected < 0) {
                            if (files.plp.exists()) files.plp.delete()
                            if (!work.renameTo(files.plp)) work.copyTo(files.plp, overwrite = true)
                        }
                        val sizesConfig = input.builtConfig ?: input.config
                        val pageSizes = PdfPageSizes.forThumbnails(files.source, sourcePages, sizesConfig, input.batchSize, tracker::stage)
                        PdfLayoutChangeOperation.Result(
                            input.crops, input.rotation, input.position, input.defaultPosition, input.builtConfig,
                            input.selected, tracker.snapshot(), pageSizes
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    files.backup.restore()
                    backedUp = false
                    throw e
                }
            }
        } finally {
            runCatching { work.delete() }
            runCatching { projectedSource.delete() }
            runCatching {
                files.workDir.listFiles()?.forEach { f ->
                    if (f.name.startsWith("edited_") || f.name.startsWith("flattened_")) f.delete()
                }
            }
        }
    }

    private fun rebuilt(tracker: ProgressTracker): PdfLayoutChangeOperation.Result {
        val total = PdfDocument(PdfIo.reader(files.source.absolutePath)).use { it.numberOfPages }
        val rotation = SourcePageArrays.rotationsSized(input.rotation, total)
        val position = SourcePageArrays.positionsSized(input.position, total, input.defaultPosition)

        val work = File(files.workDir, "${files.plp.name}.part")
        if (work.exists()) work.delete()
        PdfNupBuilder.build(
            source = files.source,
            totalSrcPages = total,
            outFile = work,
            workDir = files.workDir,
            config = input.config,
            rotation = rotation,
            position = position,
            crop = input.crops,
            batchSize = input.batchSize,
            progressStage = tracker::stage,
            warn = warn
        )
        if (files.plp.exists()) files.plp.delete()
        work.renameTo(files.plp)

        val pageSizes = PdfPageSizes.forThumbnails(files.source, total, input.config, input.batchSize, tracker::stage)
        return PdfLayoutChangeOperation.Result(
            input.crops, rotation, position, input.defaultPosition, input.config,
            input.selected, tracker.snapshot(), pageSizes
        )
    }

    suspend fun restoreIfBackedUp() {
        if (!backedUp) return
        withContext(Dispatchers.IO) {
            files.backup.restore()
        }
    }

    private fun sourcePageCount(): Int {
        if (!files.source.exists()) return 0
        return try { PdfDocument(PdfIo.reader(files.source.absolutePath)).use { it.numberOfPages } } catch (_: Exception) { 0 }
    }
}
