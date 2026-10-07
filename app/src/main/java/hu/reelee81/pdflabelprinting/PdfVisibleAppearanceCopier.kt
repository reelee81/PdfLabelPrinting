package hu.reelee81.pdflabelprinting

import com.itextpdf.forms.fields.PdfFormField
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.source.PdfTokenizer
import com.itextpdf.io.source.RandomAccessFileOrArray
import com.itextpdf.io.source.RandomAccessSourceFactory
import com.itextpdf.kernel.colors.Color
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.IPdfPageExtraCopier
import com.itextpdf.kernel.pdf.PdfArray
import com.itextpdf.kernel.pdf.PdfDictionary
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfNumber
import com.itextpdf.kernel.pdf.PdfObject
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.PdfString
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.util.PdfCanvasParser
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject
import java.io.File
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

internal class PdfVisibleAppearanceCopier(private val cacheDir: File) : IPdfPageExtraCopier {
    override fun copy(fromPage: PdfPage, toPage: PdfPage) {
        PdfCopySupport.repairInvalidBoxes(toPage)
        PdfIdentityCMapRepair.repair(toPage)
        val annotations = fromPage.pdfObject.getAsArray(PdfName.Annots) ?: return
        var canvas: PdfCanvas? = null
        fun contentCanvas(): PdfCanvas = canvas ?: PdfCanvas(toPage, true).also { canvas = it }

        try {
            for (index in 0 until annotations.size()) {
                val annotation = annotations.getAsDictionary(index) ?: continue
                val subtype = annotation.getAsName(PdfName.Subtype)
                if (subtype == PdfName.Text || subtype == PdfName.Popup || subtype == PdfName.Link) continue
                val flags = annotation.getAsNumber(PdfName.F)?.intValue() ?: 0
                if (flags and (1 or 2 or 32) != 0) continue
                val rect = annotation.getAsArray(PdfName.Rect)?.toRectangle() ?: continue
                if (!validRectangle(rect)) continue

                val appearance = normalAppearance(annotation)
                val fieldType = if (subtype == PdfName.Widget) inherited(annotation, PdfName.FT) else null
                if (appearance != null) {
                    drawAppearance(appearance, rect, contentCanvas(), toPage.document, subtype == PdfName.Widget)
                } else if (subtype == PdfName.Widget) {
                    val normal = annotation.getAsDictionary(PdfName.AP)?.getAsDictionary(PdfName.N)
                    if (normal?.get(appearanceState(annotation, normal)) is PdfName) continue
                    if (fieldType == PdfName.Tx || fieldType == PdfName.Btn || fieldType == PdfName.Ch) {
                        drawGeneratedWidget(annotation, fromPage.document, rect, contentCanvas(), toPage.document)
                    }
                } else if (subtype == PdfName.Highlight || subtype == PdfName.Underline ||
                    subtype == PdfName.StrikeOut || subtype == PdfName.Squiggly
                ) {
                    drawTextMarkup(annotation, rect, contentCanvas())
                }
            }
        } finally {
            canvas?.release()
        }

        toPage.pdfObject.remove(PdfName.Annots)
    }

    private fun normalAppearance(annotation: PdfDictionary): PdfStream? {
        val normal = annotation.getAsDictionary(PdfName.AP)?.get(PdfName.N) ?: return null
        if (normal is PdfStream) return normal
        if (normal !is PdfDictionary) return null
        return normal.getAsStream(appearanceState(annotation, normal))
    }

    private fun appearanceState(annotation: PdfDictionary, normal: PdfDictionary?): PdfName {
        annotation.getAsName(PdfName.AS)?.let { return it }
        val value = inherited(annotation, PdfName.V) as? PdfName
        val flags = (inherited(annotation, PdfName.Ff) as? PdfNumber)?.intValue() ?: 0
        val isToggle = inherited(annotation, PdfName.FT) == PdfName.Btn && flags and (1 shl 16) == 0
        val isCheckbox = isToggle && flags and (1 shl 15) == 0
        if (value != null && (normal == null || normal.containsKey(value) || isCheckbox)) return value
        if (!isToggle && normal != null && normal.size() == 1) return normal.keySet().first()
        return offState
    }

    private fun drawAppearance(
        stream: PdfStream,
        rect: Rectangle,
        canvas: PdfCanvas,
        destination: PdfDocument,
        allowMissingBounds: Boolean = false
    ) {
        val bounds = stream.getAsArray(PdfName.BBox)?.toRectangle()
            ?: if (allowMissingBounds) Rectangle(0f, 0f, rect.width, rect.height) else return
        if (!validRectangle(bounds)) return
        val copied = stream.copyTo(destination) as PdfStream
        if (!copied.containsKey(PdfName.BBox)) copied.put(PdfName.BBox, PdfArray(bounds))
        val xObject = PdfFormXObject(copied)
        val matrix = FloatArray(6)
        PdfFormXObject.calcAppearanceTransformToAnnotRect(xObject, rect).getMatrix(matrix)
        if (matrix.any { !it.isFinite() }) return
        canvas.saveState()
            .addXObjectWithTransformationMatrix(xObject, matrix[0], matrix[1], matrix[2], matrix[3], matrix[4], matrix[5])
            .restoreState()
    }

    private fun drawGeneratedWidget(
        annotation: PdfDictionary,
        source: PdfDocument,
        rect: Rectangle,
        canvas: PdfCanvas,
        destination: PdfDocument
    ) {
        val file = File.createTempFile("pdf_widget_appearance_", ".pdf", cacheDir)
        try {
            PdfDocument(PdfWriter(file.absolutePath)).use { scratch ->
                val page = scratch.addNewPage()
                val sourceForm = source.catalog.pdfObject.getAsDictionary(PdfName.AcroForm)
                val defaults = PdfDictionary()
                for (key in arrayOf(PdfName.DA, PdfName.Q)) {
                    sourceForm?.get(key)?.let { defaults.put(key, it.copyTo(scratch)) }
                }
                copyDefaultFonts(annotation, sourceForm, defaults, scratch)
                defaults.put(PdfName.Fields, PdfArray())
                scratch.catalog.pdfObject.put(PdfName.AcroForm, defaults)

                val widget = annotation.copyTo(
                    scratch, listOf(PdfName.Parent, PdfName.Kids, PdfName.P, PdfName.AA), true
                )
                for (key in inheritedFieldKeys) {
                    inherited(annotation, key)?.let { widget.put(key, it.copyTo(scratch)) }
                }
                if (!widget.containsKey(PdfName.DA)) defaults.get(PdfName.DA)?.let { widget.put(PdfName.DA, it) }
                if (!widget.containsKey(PdfName.Q)) defaults.get(PdfName.Q)?.let { widget.put(PdfName.Q, it) }
                widget.put(PdfName.T, PdfString("appearance"))
                widget.put(PdfName.P, page.pdfObject)
                val flags = widget.getAsNumber(PdfName.Ff)?.intValue() ?: 0
                if (widget.getAsName(PdfName.FT) == PdfName.Btn && flags and (1 shl 15) != 0) {
                    val state = appearanceState(annotation, annotation.getAsDictionary(PdfName.AP)?.getAsDictionary(PdfName.N))
                    if (state != offState) {
                        val normal = widget.getAsDictionary(PdfName.AP)?.getAsDictionary(PdfName.N) ?: PdfDictionary()
                        if (!normal.containsKey(state)) normal.put(state, PdfFormXObject(Rectangle(0f, 0f)).pdfObject)
                        widget.put(PdfName.AP, PdfDictionary().apply { put(PdfName.N, normal) })
                    }
                }
                widget.makeIndirect(scratch)
                val field = PdfFormField.makeFormField(widget, scratch)
                    ?: throw IOException("Cannot generate PDF form field appearance")
                if (!field.regenerateField()) throw IOException("Cannot regenerate PDF form field appearance")
                val regenerated = field.widgets.singleOrNull()
                    ?: throw IOException("Missing regenerated PDF form widget")
                page.addAnnotation(regenerated)
                defaults.getAsArray(PdfName.Fields).add(field.pdfObject)
            }
            PdfDocument(PdfReader(file.absolutePath).setMemorySavingMode(true)).use { scratch ->
                val widget = scratch.getPage(1).pdfObject.getAsArray(PdfName.Annots).getAsDictionary(0)
                val normal = widget.getAsDictionary(PdfName.AP)?.get(PdfName.N)
                val stream = (normal as? PdfStream)
                    ?: (normal as? PdfDictionary)?.getAsStream(
                        appearanceState(widget, normal)
                    )
                    ?: throw IOException("Missing regenerated PDF form field appearance")
                drawAppearance(stream, rect, canvas, destination)
                destination.flushCopiedObjects(scratch)
            }
        } finally {
            file.delete()
        }
    }

    private fun copyDefaultFonts(
        annotation: PdfDictionary,
        sourceForm: PdfDictionary?,
        defaults: PdfDictionary,
        scratch: PdfDocument
    ) {
        val appearance = inherited(annotation, PdfName.DA) as? PdfString
            ?: sourceForm?.getAsString(PdfName.DA) ?: return
        val sourceFonts = sourceForm?.getAsDictionary(PdfName.DR)?.getAsDictionary(PdfName.Font) ?: return
        val fonts = PdfDictionary()
        val bytes = PdfEncodings.convertToBytes(appearance.value, null)
        PdfTokenizer(RandomAccessFileOrArray(RandomAccessSourceFactory().createSource(bytes))).use { tokenizer ->
            val parser = PdfCanvasParser(tokenizer)
            val operands = ArrayList<PdfObject>()
            while (parser.parse(operands).isNotEmpty()) {
                if (operands.size >= 3 && operands.last().toString() == "Tf") {
                    val name = operands[operands.size - 3] as? PdfName ?: continue
                    sourceFonts.get(name)?.let { fonts.put(name, it.copyTo(scratch)) }
                }
            }
        }
        if (fonts.size() > 0) defaults.put(PdfName.DR, PdfDictionary().apply { put(PdfName.Font, fonts) })
    }

    private fun drawTextMarkup(annotation: PdfDictionary, rect: Rectangle, canvas: PdfCanvas) {
        val subtype = annotation.getAsName(PdfName.Subtype)
        val values = annotation.getAsArray(PdfName.QuadPoints)?.toFloatArray()
            ?: floatArrayOf(rect.left, rect.top, rect.right, rect.top, rect.left, rect.bottom, rect.right, rect.bottom)
        val colorValues = annotation.getAsArray(PdfName.C)?.toFloatArray()
        val color = colorValues?.let { Color.createColorWithColorSpace(it) }
            ?: if (subtype == PdfName.Highlight) ColorConstants.YELLOW else ColorConstants.BLACK
        val opacity = (annotation.getAsNumber(PdfName.CA)?.floatValue() ?: 1f).coerceIn(0f, 1f)
        val state = PdfExtGState().setFillOpacity(opacity).setStrokeOpacity(opacity)
        if (subtype == PdfName.Highlight) state.blendMode = PdfExtGState.BM_MULTIPLY
        canvas.saveState().setExtGState(state).setFillColor(color).setStrokeColor(color)
        try {
            for (offset in 0..values.size - 8 step 8) {
                val quad = values.copyOfRange(offset, offset + 8)
                if (quad.any { !it.isFinite() }) continue
                val cross = (quad[2] - quad[0]) * (quad[5] - quad[1]) -
                    (quad[3] - quad[1]) * (quad[4] - quad[0])
                val q = if (cross > 0f) floatArrayOf(
                    quad[6], quad[7], quad[4], quad[5], quad[0], quad[1], quad[2], quad[3]
                ) else quad
                if (subtype == PdfName.Highlight) {
                    canvas.moveTo(q[0].toDouble(), q[1].toDouble())
                        .lineTo(q[2].toDouble(), q[3].toDouble())
                        .lineTo(q[6].toDouble(), q[7].toDouble())
                        .lineTo(q[4].toDouble(), q[5].toDouble()).closePath().fill()
                    continue
                }
                val height = hypot(q[0] - q[4], q[1] - q[5])
                val thickness = max(0.5f, height / 12f)
                val fraction = if (subtype == PdfName.StrikeOut) 0.5f else 0.1f
                val startX = q[4] + (q[0] - q[4]) * fraction
                val startY = q[5] + (q[1] - q[5]) * fraction
                val endX = q[6] + (q[2] - q[6]) * fraction
                val endY = q[7] + (q[3] - q[7]) * fraction
                canvas.setLineWidth(thickness).moveTo(startX.toDouble(), startY.toDouble())
                if (subtype == PdfName.Squiggly) {
                    val length = hypot(endX - startX, endY - startY)
                    if (length > 0f) {
                        val steps = ceil(length / max(1f, thickness * 2f)).toInt().coerceIn(2, 4096)
                        val nx = -(endY - startY) / length * thickness
                        val ny = (endX - startX) / length * thickness
                        for (step in 1..steps) {
                            val t = step.toFloat() / steps
                            val sign = if (step == steps) 0f else if (step % 2 == 0) -1f else 1f
                            canvas.lineTo(
                                (startX + (endX - startX) * t + nx * sign).toDouble(),
                                (startY + (endY - startY) * t + ny * sign).toDouble()
                            )
                        }
                    }
                } else {
                    canvas.lineTo(endX.toDouble(), endY.toDouble())
                }
                canvas.stroke()
            }
        } finally {
            canvas.restoreState()
        }
    }

    private fun inherited(annotation: PdfDictionary, key: PdfName): PdfObject? {
        var current: PdfDictionary? = annotation
        val visited = HashSet<PdfDictionary>()
        while (current != null && visited.add(current)) {
            current.get(key)?.let { return it }
            current = current.getAsDictionary(PdfName.Parent)
        }
        return null
    }

    private fun validRectangle(rect: Rectangle): Boolean =
        rect.x.isFinite() && rect.y.isFinite() && rect.width.isFinite() && rect.height.isFinite() &&
            rect.width > 0f && rect.height > 0f

    private companion object {
        val offState = PdfName("Off")
        val inheritedFieldKeys = arrayOf(
            PdfName.FT, PdfName.Ff, PdfName.V, PdfName.DV, PdfName.DA, PdfName.Q,
            PdfName.MaxLen, PdfName.Opt, PdfName.I, PdfName.TI, PdfName.DS, PdfName.RV
        )
    }
}