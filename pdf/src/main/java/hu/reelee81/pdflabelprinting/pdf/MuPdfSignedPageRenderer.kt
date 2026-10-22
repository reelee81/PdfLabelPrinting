package hu.reelee81.pdflabelprinting.pdf

import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Context
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.DrawDevice
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Pixmap
import com.artifex.mupdf.fitz.Rect
import hu.reelee81.pdflabelprinting.core.SignatureGeometry
import hu.reelee81.pdflabelprinting.core.SignedPageEffects
import java.io.File

class MuPdfSignedPageRenderer(
    private val warn: (message: String, error: Throwable) -> Unit = { _, _ -> }
) : SignedPageRenderer {
    override fun open(layout: File): SignedPageRenderer.Pages {
        val path = layout.absolutePath
        val count = withDocument(path) { it.countPages() }
        return Pages(path, count)
    }

    private inner class Pages(
        private val path: String,
        override val pageCount: Int
    ) : SignedPageRenderer.Pages {
        override fun render(pageIndex: Int, signature: SignedPageRenderer.Signature): SignedPageRenderer.RgbImage? =
            withDocument(path) { document ->
                val page = document.loadPage(pageIndex)
                try {
                    renderPage(page, pageIndex, signature)
                } finally {
                    page.destroy()
                }
            }

        override fun close() {}
    }

    private fun renderPage(page: Page, idx: Int, signature: SignedPageRenderer.Signature): SignedPageRenderer.RgbImage? {
        val bounds = page.bounds
        val raster = SignatureGeometry.pageRaster(
            (bounds.x1 - bounds.x0).toInt(),
            (bounds.y1 - bounds.y0).toInt(),
            signature.refPx,
            signature.isLandscape
        )
        val width = raster.width
        val height = raster.height

        val rgb = renderRgb(page, bounds, raster)

        if (signature.grayscale) SignedPageEffects.grayscale(rgb)

        val placement = SignatureGeometry.placement(
            width,
            height,
            signature.width,
            signature.height,
            signature.refPx,
            signature.xMm,
            signature.yMm,
            signature.isLandscape
        )
        if (!drawSignature(rgb, width, height, signature.file, placement, idx)) return null

        if (signature.scanEffect) SignedPageEffects.scanEffect(rgb, width, height)

        return SignedPageRenderer.RgbImage(width, height, rgb)
    }

    private fun renderRgb(page: Page, bounds: Rect, raster: SignatureGeometry.PageRaster): ByteArray {
        val pixmap = Pixmap(ColorSpace.DeviceRGB, 0, 0, raster.width, raster.height, false)
        try {
            pixmap.clear(255)
            val ctm = Matrix(
                raster.scale, 0f, 0f, raster.scale,
                raster.offsetX - bounds.x0 * raster.scale,
                raster.offsetY - bounds.y0 * raster.scale
            )
            val device = DrawDevice(pixmap)
            try {
                page.run(device, ctm, null)
                device.close()
            } finally {
                device.destroy()
            }
            return packed(pixmap, raster.width, raster.height)
        } finally {
            pixmap.destroy()
        }
    }

    private fun drawSignature(
        rgb: ByteArray,
        width: Int,
        height: Int,
        file: File,
        placement: SignatureGeometry.Placement,
        idx: Int
    ): Boolean {
        val decoded = try {
            MuPdfSignatureImage.read(file)
        } catch (e: Exception) {
            warn("Signature decode failed at page $idx", e)
            return false
        }
        SignedPageEffects.drawNearest(
            rgb, width, height,
            SignedPageEffects.subsample(decoded, placement.sampleSize),
            placement.left, placement.top, placement.width, placement.height
        )
        return true
    }

    private fun packed(pixmap: Pixmap, width: Int, height: Int): ByteArray {
        val samples = pixmap.samples
        val row = width * 3
        val stride = pixmap.stride
        if (stride == row && samples.size == row * height) return samples
        val out = ByteArray(row * height)
        for (y in 0 until height) System.arraycopy(samples, y * stride, out, y * row, row)
        return out
    }

    private companion object {
        fun <T> withDocument(path: String, block: (Document) -> T): T {
            val document = Document.openDocument(path)
            try {
                return block(document)
            } finally {
                document.destroy()
                Context.emptyStore()
            }
        }
    }
}
