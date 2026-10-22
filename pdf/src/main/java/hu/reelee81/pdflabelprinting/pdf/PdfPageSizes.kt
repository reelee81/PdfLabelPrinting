package hu.reelee81.pdflabelprinting.pdf

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import hu.reelee81.pdflabelprinting.core.NupConfig
import hu.reelee81.pdflabelprinting.core.PageSizes
import java.io.File
import kotlin.math.max
import kotlin.math.min

object PdfPageSizes {
    fun isNeeded(config: NupConfig): Boolean {
        val rows = max(1, min(100, config.rows))
        val cols = max(1, min(100, config.cols))
        return rows == 1 && cols == 1
    }

    fun fallback(config: NupConfig): Pair<Float, Float> {
        val rows = max(1, min(100, config.rows))
        val cols = max(1, min(100, config.cols))
        val isLandscape = config.isLandscape
        val basePs = if (isLandscape) PageSize.A4.rotate() else PageSize.A4
        val isSingleUp = (rows * cols) == 1

        val psForPlpPageFallback = if (isSingleUp) {
            if (!isLandscape) PageSize.A4 else basePs
        } else {
            basePs
        }
        return psForPlpPageFallback.width to psForPlpPageFallback.height
    }

    fun forThumbnails(
        source: File,
        totalPages: Int,
        config: NupConfig,
        batchSize: Int,
        progressStage: (Long) -> PdfProgress
    ): PageSizes? {
        if (!isNeeded(config)) return null
        val (fallbackW, fallbackH) = fallback(config)
        return read(source, totalPages, batchSize, fallbackW, fallbackH, progressStage(totalPages.toLong()))
    }

    fun read(
        source: File,
        totalPages: Int,
        batchSize: Int,
        fallbackW: Float,
        fallbackH: Float,
        progress: PdfProgress
    ): PageSizes {
        val widths  = FloatArray(totalPages) { fallbackW }
        val heights = FloatArray(totalPages) { fallbackH }

        var startPage = 0
        while (startPage < totalPages) {
            val endExclusive = min(totalPages, startPage + batchSize)

            val pdfDoc = PdfDocument(PdfIo.reader(source.absolutePath))
            try {
                val srcPageCount = pdfDoc.numberOfPages
                val lastPageToRead = min(endExclusive, srcPageCount)

                for (pageZeroBased in startPage until lastPageToRead) {
                    val pageIndex1Based = pageZeroBased + 1
                    runCatching {
                        val page = pdfDoc.getPage(pageIndex1Based)

                        val box = page.cropBox ?: page.mediaBox
                        val w = box.width
                        val h = box.height

                        widths[pageZeroBased] = w
                        heights[pageZeroBased] = h
                    }.onFailure {
                    }
                }
            } finally {
                runCatching { pdfDoc.close() }
            }

            progress.advance(endExclusive - startPage)
            startPage = endExclusive
        }
        progress.idle()

        return PageSizes(widths, heights)
    }
}
