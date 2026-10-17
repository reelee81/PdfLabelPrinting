package hu.reelee81.pdflabelprinting

import com.itextpdf.io.source.PdfTokenizer
import com.itextpdf.io.source.RandomAccessFileOrArray
import com.itextpdf.io.source.RandomAccessSourceFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfArray
import com.itextpdf.kernel.pdf.PdfDictionary
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfObject
import com.itextpdf.kernel.pdf.PdfOutputStream
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfResources
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.util.PdfCanvasParser
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

internal object PdfCopySupport {
    private val WRAPPER_TOKEN: Pattern = Pattern.compile(
        "\\s*(?:q|Q|(?:[-+]?(?:\\d+\\.?\\d*|\\.\\d+)\\s+){6}cm|/([^\\s/<>\\[\\]()%{}]+)\\s*Do)"
    )
    private val REF = PdfName("Ref")
    private const val MAX_WRAPPER_DEPTH = 64
    private const val MAX_REFERENCE_DEPTH = 16
    private const val MAX_REFERENCE_FORMS = 256
    private const val MATRIX_SIZE = 6

    private class Wrapper(val before: String, val name: PdfName, val after: String)

    fun flattenedFormXObject(page: PdfPage, destination: PdfDocument, bBox: Rectangle): PdfFormXObject? {
        val dictionary = page.pdfObject
        if (page.rotation % 360 != 0 || dictionary.containsKey(PdfName.Group) || dictionary.containsKey(PdfName.UserUnit)) {
            return null
        }
        val pageContent = ByteArrayOutputStream()
        for (index in 0 until page.contentStreamCount) {
            pageContent.write(page.getContentStream(index).bytes)
            pageContent.write('\n'.code)
        }
        var wrapper = parseWrapper(pageContent.toByteArray()) ?: return null
        var resources: PdfDictionary = page.resources.pdfObject
        val output = ByteArrayOutputStream()
        val closing = ArrayList<String>()
        val seen = HashSet<PdfStream>()
        repeat(MAX_WRAPPER_DEPTH) {
            val form = resources.getAsDictionary(PdfName.XObject)?.getAsStream(wrapper.name) ?: return null
            if (!seen.add(form) || !isPlainForm(form)) return null
            val box = form.getAsArray(PdfName.BBox)?.takeIf { it.size() == 4 }?.toRectangle() ?: return null
            val matrix = form.getAsArray(PdfName.Matrix)?.let { matrixValues(it) ?: return null }
            val formResources = form.getAsDictionary(PdfName.Resources) ?: return null

            output.writeLatin1(wrapper.before)
            output.writeLatin1("\nq\n")
            if (matrix != null) output.writeOperator(matrix, "cm")
            output.writeOperator(doubleArrayOf(box.x.toDouble(), box.y.toDouble(), box.width.toDouble(), box.height.toDouble()), "re W n")
            closing += wrapper.after

            val content = form.bytes
            val next = parseWrapper(content)?.takeIf { candidate ->
                formResources.getAsDictionary(PdfName.XObject)?.getAsStream(candidate.name)
                    ?.getAsName(PdfName.Subtype) == PdfName.Form
            }
            if (next == null) {
                if (refersToChain(formResources, seen) || PdfEditProjection.containsEditLayer(formResources)) return null
                val unclosed = unclosedSaves(content, formResources) ?: return null
                output.write(content)
                repeat(unclosed) { output.writeLatin1("\nQ") }
                for (after in closing.asReversed()) {
                    output.writeLatin1("\nQ\n")
                    output.writeLatin1(after)
                }
                return PdfFormXObject(bBox).also { flattened ->
                    flattened.pdfObject.put(PdfName.Resources, formResources.copyTo(destination, false))
                    form.get(PdfName.Group)?.let { flattened.pdfObject.put(PdfName.Group, it.copyTo(destination, false)) }
                    flattened.pdfObject.setData(output.toByteArray())
                }
            }
            if (form.containsKey(PdfName.Group)) return null
            wrapper = next
            resources = formResources
        }
        return null
    }

    private fun parseWrapper(bytes: ByteArray): Wrapper? {
        val text = String(bytes, Charsets.ISO_8859_1)
        val matcher = WRAPPER_TOKEN.matcher(text)
        var position = 0
        var name: String? = null
        var start = 0
        var end = 0
        while (position < text.length) {
            matcher.region(position, text.length)
            if (!matcher.lookingAt()) {
                if (text.substring(position).isNotBlank()) return null
                break
            }
            val found = matcher.group(1)
            if (found != null) {
                if (name != null) return null
                name = found
                start = matcher.start()
                end = matcher.end()
            }
            position = matcher.end()
        }
        val target = name ?: return null
        return Wrapper(text.substring(0, start), PdfName(target), text.substring(end))
    }

    private fun refersToChain(resources: PdfDictionary, chain: Set<PdfStream>): Boolean {
        val references = chain.mapNotNullTo(HashSet()) { it.indirectReference }
        val visited = HashSet<PdfStream>()
        var level = listOf(resources)
        repeat(MAX_REFERENCE_DEPTH) {
            val next = ArrayList<PdfDictionary>()
            for (dictionary in level) {
                val xObjects = dictionary.getAsDictionary(PdfName.XObject) ?: continue
                for (name in xObjects.keySet()) {
                    val stream = xObjects.getAsStream(name) ?: continue
                    if (stream in chain || stream.indirectReference?.let { it in references } == true) return true
                    if (stream.getAsName(PdfName.Subtype) != PdfName.Form || !visited.add(stream)) continue
                    if (visited.size > MAX_REFERENCE_FORMS) return true
                    stream.getAsDictionary(PdfName.Resources)?.let(next::add)
                }
            }
            if (next.isEmpty()) return false
            level = next
        }
        return true
    }

    private fun isPlainForm(form: PdfStream): Boolean =
        form.getAsName(PdfName.Subtype) == PdfName.Form &&
            !form.containsKey(PdfName.OC) &&
            !form.containsKey(REF) &&
            !PdfEditProjection.isEditLayer(form)

    private fun matrixValues(array: PdfArray): DoubleArray? {
        if (array.size() != MATRIX_SIZE) return null
        val values = DoubleArray(MATRIX_SIZE)
        for (index in 0 until MATRIX_SIZE) {
            val value = array.getAsNumber(index)?.doubleValue() ?: return null
            if (!value.isFinite()) return null
            values[index] = value
        }
        return values
    }

    private fun unclosedSaves(content: ByteArray, resources: PdfDictionary): Int? {
        return try {
            PdfTokenizer(RandomAccessFileOrArray(RandomAccessSourceFactory().createSource(content))).use { tokenizer ->
                val parser = PdfCanvasParser(tokenizer, PdfResources(resources))
                val operands = ArrayList<PdfObject>()
                var depth = 0
                while (parser.parse(operands).isNotEmpty()) {
                    when (operands.last().toString()) {
                        "q" -> depth++
                        "Q" -> if (--depth < 0) return null
                    }
                }
                depth
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ByteArrayOutputStream.writeLatin1(text: String) {
        write(text.toByteArray(Charsets.ISO_8859_1))
    }

    private fun ByteArrayOutputStream.writeOperator(values: DoubleArray, operator: String) {
        val output = PdfOutputStream(this)
        for (value in values) output.writeDouble(value, true).writeSpace()
        output.writeString("$operator\n")
        output.flush()
    }

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

    fun repairInvalidBoxes(page: PdfPage): Boolean {
        if (!validRectangle(page.pageSize)) page.setMediaBox(Rectangle(PageSize.LETTER))
        val media = page.pageSize
        val crop = page.cropBox
        if (!validRectangle(crop)) {
            page.setCropBox(Rectangle(media))
            return true
        }
        if (crop.right <= media.left || crop.left >= media.right || crop.top <= media.bottom || crop.bottom >= media.top) {
            page.setCropBox(Rectangle(media))
            page.pdfObject.remove(PdfName.Contents)
            return false
        }
        return true
    }

    fun repairContents(page: PdfPage) {
        val dictionary = page.pdfObject
        val contents = try {
            dictionary.getAsArray(PdfName.Contents)
        } catch (_: RuntimeException) {
            null
        } ?: return
        val valid = PdfArray()
        for (index in 0 until contents.size()) {
            val stream = try {
                contents.get(index) as? PdfStream
            } catch (_: RuntimeException) {
                null
            } ?: continue
            valid.add(contents.get(index, false) ?: stream)
        }
        if (valid.size() == contents.size()) return
        if (valid.isEmpty) dictionary.remove(PdfName.Contents) else dictionary.put(PdfName.Contents, valid)
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