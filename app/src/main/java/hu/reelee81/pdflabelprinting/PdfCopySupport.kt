package hu.reelee81.pdflabelprinting

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import kotlin.math.max
import kotlin.math.min

internal object PdfCopySupport {
    fun visibleBox(page: PdfPage): Rectangle {
        val media = page.pageSize.takeIf(::validRectangle) ?: Rectangle(PageSize.LETTER)
        val crop = page.cropBox.takeIf(::validRectangle) ?: return media
        val left = max(media.left, crop.left)
        val bottom = max(media.bottom, crop.bottom)
        val right = min(media.right, crop.right)
        val top = min(media.top, crop.top)
        return if (right > left && top > bottom) {
            Rectangle(left, bottom, right - left, top - bottom)
        } else media
    }

    fun repairInvalidBoxes(page: PdfPage) {
        if (!validRectangle(page.pageSize)) page.setMediaBox(Rectangle(PageSize.LETTER))
        val media = page.pageSize
        val crop = page.cropBox
        if (!validRectangle(crop) || crop.right <= media.left || crop.left >= media.right ||
            crop.top <= media.bottom || crop.bottom >= media.top
        ) {
            page.setCropBox(Rectangle(media))
        }
    }

    fun flushCopiedObjects(destination: PdfDocument, source: PdfDocument) {
        if (destination.catalog.pdfObject.containsKey(PdfName.OCProperties)) {
            destination.catalog.getOCProperties(false)
        }
        destination.flushCopiedObjects(source)
    }

    fun concatPlacementMatrix(canvas: PdfCanvas, a: Double, b: Double, c: Double, d: Double, e: Double, f: Double) {
        canvas.graphicsState.updateCtm(a.toFloat(), b.toFloat(), c.toFloat(), d.toFloat(), e.toFloat(), f.toFloat())
        val output = canvas.contentStream.outputStream
        for (number in doubleArrayOf(a, b, c, d, e, f)) output.writeDouble(number, true).writeSpace()
        output.writeString("cm\n")
    }

    private fun validRectangle(rect: Rectangle): Boolean =
        rect.x.isFinite() && rect.y.isFinite() && rect.width.isFinite() && rect.height.isFinite() &&
            rect.right.isFinite() && rect.top.isFinite() &&
            rect.width > 0f && rect.height > 0f
}