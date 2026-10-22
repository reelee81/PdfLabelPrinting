package hu.reelee81.pdflabelprinting.pdf

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import java.io.File
import kotlin.math.min

object PdfSourceRewrite {
    fun write(
        source: File,
        work: File,
        runs: List<IntRange>,
        batchSize: Int,
        progress: PdfProgress,
        emptyPage: PageSize? = null
    ) {
        if (work.exists()) work.delete()

        PdfDocument(PdfWriter(work.absolutePath, PdfIo.writerProperties())).use { dest ->
            dest.setFlushUnusedObjects(true)

            for (run in runs) copyRange(source, dest, run.first, run.last, batchSize, progress)

            if (emptyPage != null) {
                val page = dest.addNewPage(emptyPage)
                runCatching { page.flush() }
            }
            progress.idle()
        }

        if (source.exists()) source.delete()
        work.renameTo(source)
    }

    fun writeReplacingGroups(
        source: File,
        work: File,
        totalPages: Int,
        perGroup: Int,
        replacements: Map<Int, File>,
        batchSize: Int,
        progress: PdfProgress
    ) {
        if (work.exists()) work.delete()

        PdfDocument(PdfWriter(work.absolutePath, PdfIo.writerProperties())).use { dest ->
            dest.setFlushUnusedObjects(true)

            var p = 1
            var runStart: Int? = null

            while (p <= totalPages) {
                val groupIndex = (p - 1) / perGroup
                val replacement = replacements[groupIndex]

                if (replacement != null) {
                    runStart?.let { copyRange(source, dest, it, p - 1, batchSize, progress) }
                    runStart = null

                    PdfDocument(PdfIo.reader(replacement.absolutePath)).use { repl ->
                        repl.copyPagesTo(1, 1, dest)
                        runCatching { PdfCopySupport.flushCopiedObjects(dest, repl) }
                    }
                    runCatching { dest.lastPage?.flush() }

                    val groupStart = groupIndex * perGroup + 1
                    val groupEnd = min(totalPages, groupStart + perGroup - 1)
                    progress.advance(groupEnd - groupStart + 1)
                    p = groupEnd + 1
                } else {
                    if (runStart == null) runStart = p
                    p++
                }
            }

            runStart?.let { copyRange(source, dest, it, totalPages, batchSize, progress) }
            progress.idle()
        }

        if (source.exists()) source.delete()
        work.renameTo(source)
    }

    private fun copyRange(source: File, dest: PdfDocument, from1: Int, to1: Int, batchSize: Int, progress: PdfProgress) {
        var from = from1
        while (from <= to1) {
            val to = min(from + batchSize - 1, to1)
            PdfDocument(PdfIo.reader(source.absolutePath)).use { src ->
                src.copyPagesTo(from, to, dest)
                runCatching { PdfCopySupport.flushCopiedObjects(dest, src) }
            }
            val added = to - from + 1
            for (i in 0 until added) {
                val p = dest.numberOfPages - i
                if (p >= 1) runCatching { dest.getPage(p).flush() }
            }
            progress.advance(added)
            from = to + 1
        }
    }
}
