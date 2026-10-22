package hu.reelee81.pdflabelprinting.pdf

import com.itextpdf.kernel.exceptions.BadPasswordException
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import hu.reelee81.pdflabelprinting.core.NupConfig
import hu.reelee81.pdflabelprinting.core.PageGroups
import hu.reelee81.pdflabelprinting.core.PdfCropRect
import hu.reelee81.pdflabelprinting.core.ProgressMath
import hu.reelee81.pdflabelprinting.core.ProgressNotifyPacing
import hu.reelee81.pdflabelprinting.core.ProgressTicker
import hu.reelee81.pdflabelprinting.core.SourcePageArrays
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PdfImportOperation(
    private val files: PdfLayoutChangeOperation.Files,
    private val saveSelection: () -> Unit,
    private val input: Input,
    private val progress: PdfLayoutChangeOperation.Progress,
    private val imageToPdf: (File) -> File,
    private val repair: Repair = Repair(PdfMuPdfRepair::withRepairedParts),
    private val warn: (message: String, error: Throwable) -> Unit = { _, _ -> }
) {
    class Source(val file: File, val password: ByteArray?, val openFailure: Exception?, val isImage: Boolean = false)

    class Input(
        val sources: List<Source>,
        val oldPerGroup: Int,
        val config: NupConfig,
        val crops: Map<Int, PdfCropRect>,
        val rotation: IntArray,
        val position: IntArray,
        val defaultPosition: Int,
        val batchSize: Int,
        val selected: List<Int>
    )

    fun interface Repair {
        fun withRepairedParts(
            input: File,
            password: ByteArray?,
            workDir: File,
            batchSize: Int,
            ensureActive: () -> Unit,
            block: (List<File>) -> Unit
        ): Boolean
    }

    class Probe(val canOpen: Boolean, val noPages: Boolean, val failure: Exception?)

    @Volatile private var backedUp = false

    suspend fun execute(report: (Int?) -> Unit): PdfLayoutChangeOperation.Result = withContext(Dispatchers.IO) {
        val tracker = ProgressTracker(progress.clock)
        tracker.reset()
        val job = coroutineContext[Job]
        val ensureActive = { job?.ensureActive(); Unit }

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
                saveSelection()

                val oldTotal = sourcePageCount()
                val selectedPages: Set<Int> = if (input.selected.isNotEmpty() && oldTotal > 0) {
                    PageGroups.sourcePagesOfGroups(input.selected, input.oldPerGroup, oldTotal)
                } else emptySet()

                files.backup.backup()
                backedUp = true

                try {
                    var rotation = input.rotation
                    var position = input.position
                    for (source in input.sources) {
                        if (source.isImage) {
                            val imagePdf = imageToPdf(source.file)
                            try {
                                append(listOf(imagePdf), null, tracker)
                            } finally {
                                runCatching { imagePdf.delete() }
                            }
                        } else {
                            appendOrRepair(source, tracker, ensureActive)
                        }
                        runCatching { source.file.delete() }

                        val grownTotal = sourcePageCount()
                        rotation = SourcePageArrays.rotationsGrown(rotation, grownTotal)
                        position = SourcePageArrays.positionsSized(position, grownTotal, input.defaultPosition)
                    }

                    val total = PdfDocument(PdfIo.reader(files.source.absolutePath)).use { it.numberOfPages }
                    rotation = SourcePageArrays.rotationsSized(rotation, total)
                    position = SourcePageArrays.positionsSized(position, total, input.defaultPosition)

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

                    val per = max(1, min(100, input.config.rows)) * max(1, min(100, input.config.cols))
                    val selected = if (selectedPages.isNotEmpty()) {
                        PageGroups.groupsOfSourcePages(selectedPages, per).toList()
                    } else emptyList()

                    PdfLayoutChangeOperation.Result(
                        input.crops, rotation, position, input.defaultPosition, input.config,
                        selected, tracker.snapshot(), pageSizes
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    files.backup.restore()
                    backedUp = false
                    throw e
                }
            }
        } finally {
            for (source in input.sources) runCatching { source.file.delete() }
        }
    }

    private fun appendOrRepair(source: Source, tracker: ProgressTracker, ensureActive: () -> Unit) {
        val doneBefore = tracker.doneUnits()
        val failure = source.openFailure ?: try {
            append(listOf(source.file), source.password, tracker)
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tracker.resetDoneUnits(doneBefore)
            e
        }

        ensureActive()
        val repaired = repair.withRepairedParts(
            input = source.file,
            password = source.password,
            workDir = files.workDir,
            batchSize = input.batchSize,
            ensureActive = ensureActive
        ) { parts -> append(parts, null, tracker) }
        if (repaired) return

        ensureActive()
        val recovered = PdfRevisionRecovery.withEarlierRevision(
            input = source.file,
            workDir = files.workDir,
            ensureActive = ensureActive,
            isUsable = { revision ->
                try {
                    PdfDocument(PdfIo.reader(revision.absolutePath, source.password)).use { it.numberOfPages > 0 }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
            }
        ) { revision -> append(listOf(revision), source.password, tracker) }
        if (!recovered) throw failure
    }

    private fun append(srcPdfs: List<File>, password: ByteArray?, tracker: ProgressTracker) {
        val outWork = files.sourceWork
        if (outWork.exists()) outWork.delete()

        val pageBatch = input.batchSize

        fun totalPagesOf(path: String, pw: ByteArray?): Int =
            PdfDocument(PdfIo.reader(path, pw)).use { it.numberOfPages }

        val inputs = buildList {
            if (files.source.exists()) add(files.source.absolutePath to null)
            for (srcPdf in srcPdfs) add(srcPdf.absolutePath to password)
        }.map { (path, pw) -> Triple(path, pw, totalPagesOf(path, pw)) }
        val allPages = inputs.sumOf { it.third }
        val per = (input.config.rows * input.config.cols).coerceAtLeast(1)
        val stage = tracker.stage(allPages + ProgressMath.layoutUnits(allPages, per))

        PdfDocument(PdfWriter(outWork.absolutePath, PdfIo.writerProperties())).use { dest ->
            dest.setFlushUnusedObjects(true)

            fun copyInBatches(inputPath: String, pw: ByteArray?, total: Int) {
                var from = 1
                while (from <= total) {
                    val to = min(from + pageBatch - 1, total)

                    PdfDocument(PdfIo.reader(inputPath, pw)).use { src ->
                        src.copyPagesTo(from, to, dest, PdfVisibleAppearanceCopier(files.workDir))
                        runCatching { PdfCopySupport.flushCopiedObjects(dest, src) }
                    }

                    val added = to - from + 1
                    for (i in 0 until added) {
                        val p = dest.numberOfPages - i
                        if (p >= 1) runCatching { dest.getPage(p).flush() }
                    }
                    stage.advance(added)
                    from = to + 1
                }
            }

            for ((path, pw, total) in inputs) copyInBatches(path, pw, total)
            stage.idle()
        }

        if (files.source.exists()) files.source.delete()
        outWork.renameTo(files.source)
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

    companion object {
        fun probe(
            file: File,
            password: ByteArray?,
            rejectsPassword: (File, ByteArray?) -> Boolean = PdfMuPdfRepair::rejectsPassword
        ): Probe = try {
            val (encrypted, pageCount) = PdfDocument(PdfIo.reader(file.absolutePath, password)).use {
                it.reader.isEncrypted to it.numberOfPages
            }
            Probe(canOpen = !encrypted || !rejectsPassword(file, password), noPages = pageCount == 0, failure = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isBadPassword(e)) {
                Probe(canOpen = false, noPages = false, failure = null)
            } else {
                Probe(canOpen = true, noPages = false, failure = e)
            }
        }

        private fun isBadPassword(t: Throwable): Boolean {
            if (t is BadPasswordException) return true
            val msg = t.message?.lowercase().orEmpty()
            return msg.contains("bad user password") || msg.contains("bad password")
        }
    }
}
