package hu.reelee81.pdflabelprinting

import com.itextpdf.io.source.PdfTokenizer
import com.itextpdf.io.source.RandomAccessFileOrArray
import com.itextpdf.io.source.RandomAccessSourceFactory
import com.itextpdf.kernel.pdf.PdfArray
import com.itextpdf.kernel.pdf.PdfBoolean
import com.itextpdf.kernel.pdf.PdfDictionary
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfNumber
import com.itextpdf.kernel.pdf.PdfObject
import com.itextpdf.kernel.pdf.PdfOutputStream
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfResources
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.util.PdfCanvasParser
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.min

internal object PdfEditProjection {
    class Cell(val sourceIndex: Int, val matrix: DoubleArray)

    class NotProjectableException(message: String) : Exception(message)

    private val EDIT_BASE = PdfName("PdfLabelEditBase")
    private val EDIT_FORM = PdfName("PdfLabelEdit")
    private val LAYER_TAG = PdfName("PdfLabelEditLayer")
    private val LAYER_SOURCE = PdfName("PdfLabelEditSource")
    private val STREAMS = PdfName("Streams")
    private val LENGTH = PdfName("Length")
    private val CELLS = PdfName("Cells")
    private const val NAME_PREFIX = "PLE"
    private const val MAX_DEPTH = 8

    fun markPlpPage(page: PdfPage, cells: List<Cell>) {
        val streams = page.contentStreamCount
        var length = 0L
        for (index in 0 until streams) length += page.getContentStream(index).bytes.size
        val array = PdfArray()
        for (cell in cells) {
            array.add(PdfNumber(cell.sourceIndex))
            for (value in cell.matrix) array.add(PdfNumber(value))
        }
        page.pdfObject.put(EDIT_BASE, PdfDictionary().apply {
            put(STREAMS, PdfNumber(streams))
            put(LENGTH, PdfNumber(length.toDouble()))
            put(CELLS, array)
        })
    }

    fun tagLayerSource(form: PdfFormXObject, sourceIndex: Int) {
        val xObjects = form.pdfObject.getAsDictionary(PdfName.Resources)?.getAsDictionary(PdfName.XObject) ?: return
        for (name in xObjects.keySet()) {
            val stream = xObjects.getAsStream(name) ?: continue
            if (stream.containsKey(LAYER_TAG)) stream.put(LAYER_SOURCE, PdfNumber(sourceIndex))
        }
    }

    fun inverse(m: DoubleArray): DoubleArray? {
        val det = m[0] * m[3] - m[1] * m[2]
        if (!det.isFinite() || abs(det) < 1e-12) return null
        return doubleArrayOf(
            m[3] / det, -m[1] / det, -m[2] / det, m[0] / det,
            (m[2] * m[5] - m[3] * m[4]) / det,
            (m[1] * m[4] - m[0] * m[5]) / det
        )
    }

    fun hasLayers(
        open: () -> PdfReader,
        pages: Set<Int>?,
        batchSize: Int,
        ensureActive: () -> Unit
    ): Boolean {
        val total = PdfDocument(open().setMemorySavingMode(true)).use { it.numberOfPages }
        val wanted = pages?.filter { it in 1..total }?.sorted() ?: (1..total).toList()
        for (chunk in wanted.chunked(batchSize.coerceAtLeast(1))) {
            ensureActive()
            val found = PdfDocument(open().setMemorySavingMode(true)).use { pdf ->
                chunk.any { number ->
                    val page = pdf.getPage(number)
                    appendedFrom(page) != null ||
                        layersOf(page.resources.pdfObject).any { it.bytes.isNotEmpty() }
                }
            }
            if (found) return true
        }
        return false
    }

    fun clearLayers(
        input: File,
        output: File,
        pages: Set<Int>?,
        batchSize: Int,
        ensureActive: () -> Unit
    ): Int = clearLayers({ PdfReader(input) }, output, pages, batchSize, ensureActive)

    fun clearLayers(
        open: () -> PdfReader,
        output: File,
        pages: Set<Int>?,
        batchSize: Int,
        ensureActive: () -> Unit
    ): Int {
        val total = PdfDocument(open().setMemorySavingMode(true)).use { it.numberOfPages }
        val batch = batchSize.coerceAtLeast(1)
        var cleared = 0
        PdfDocument(PdfWriter(output)).use { destination ->
            destination.setFlushUnusedObjects(true)
            var from = 1
            while (from <= total) {
                ensureActive()
                val to = min(from + batch - 1, total)
                PdfDocument(open().setMemorySavingMode(true)).use { src ->
                    src.copyPagesTo(from, to, destination)
                    for (number in from..to) {
                        if (pages != null && number !in pages) continue
                        val page = destination.getPage(number)
                        appendedFrom(page)?.let { streams ->
                            removeContentStreamsFrom(page, streams)
                            cleared++
                        }
                        for (layer in layersOf(page.resources.pdfObject)) {
                            if (layer.bytes.isEmpty()) continue
                            layer.remove(PdfName.Filter)
                            layer.remove(PdfName.DecodeParms)
                            layer.setData(ByteArray(0))
                            cleared++
                        }
                    }
                    destination.flushCopiedObjects(src)
                }
                for (number in from..to) destination.getPage(number).flush()
                from = to + 1
            }
        }
        return cleared
    }

    private class Edit(val plpIndex: Int, val matrix: DoubleArray)

    fun project(
        source: File,
        editedPlp: File,
        output: File,
        batchSize: Int,
        ensureActive: () -> Unit
    ): Int {
        val bySource = HashMap<Int, MutableList<Edit>>()
        val removals = HashSet<Int>()
        val plpPages = PdfDocument(PdfReader(editedPlp).setMemorySavingMode(true)).use { it.numberOfPages }
        val batch = batchSize.coerceAtLeast(1)
        var from = 1
        while (from <= plpPages) {
            ensureActive()
            val to = min(from + batch - 1, plpPages)
            PdfDocument(PdfReader(editedPlp).setMemorySavingMode(true)).use { pdf ->
                for (number in from..to) {
                    val page = pdf.getPage(number)
                    val base = page.pdfObject.getAsDictionary(EDIT_BASE)
                        ?: throw NotProjectableException("page $number has no layout record")
                    for (layer in layersOf(page.resources.pdfObject)) {
                        val sourceIndex = layer.getAsNumber(LAYER_SOURCE)?.intValue() ?: continue
                        if (layer.bytes.isEmpty()) removals += sourceIndex
                    }
                    val streams = base.getAsNumber(STREAMS)?.intValue() ?: -1
                    if (page.contentStreamCount <= streams) continue
                    var length = 0L
                    for (index in 0 until streams) length += page.getContentStream(index).bytes.size
                    if (streams < 0 || length != base.getAsNumber(LENGTH)?.longValue()) {
                        throw NotProjectableException("page $number original content was modified")
                    }
                    val cells = base.getAsArray(CELLS) ?: continue
                    var offset = 0
                    while (offset + 7 <= cells.size()) {
                        val sourceIndex = cells.getAsNumber(offset).intValue()
                        val matrix = DoubleArray(6) { cells.getAsNumber(offset + 1 + it).doubleValue() }
                        bySource.getOrPut(sourceIndex) { ArrayList() } += Edit(number, matrix)
                        offset += 7
                    }
                }
            }
            from = to + 1
        }
        if (bySource.isEmpty() && removals.isEmpty()) return 0

        val sourcePages = PdfDocument(PdfReader(source).setMemorySavingMode(true)).use { it.numberOfPages }
        if ((bySource.keys + removals).any { it !in 0 until sourcePages }) {
            throw NotProjectableException("layout record does not match the source")
        }
        PdfDocument(PdfWriter(output)).use { destination ->
            destination.setFlushUnusedObjects(true)
            var start = 1
            while (start <= sourcePages) {
                ensureActive()
                val end = min(start + batch - 1, sourcePages)
                PdfDocument(PdfReader(source).setMemorySavingMode(true)).use { src ->
                    src.copyPagesTo(start, end, destination)
                    for (pageNumber in start..end) {
                        if (pageNumber - 1 in removals) removeLayer(destination.getPage(pageNumber))
                    }
                    val affected = (start..end).filter { bySource.containsKey(it - 1) }
                    if (affected.isNotEmpty()) {
                        PdfDocument(PdfReader(editedPlp).setMemorySavingMode(true)).use { plp ->
                            for (pageNumber in affected) {
                                val target = destination.getPage(pageNumber)
                                for (edit in bySource.getValue(pageNumber - 1)) {
                                    appendEdit(target, plp.getPage(edit.plpIndex), edit.matrix)
                                }
                            }
                            destination.flushCopiedObjects(plp)
                        }
                    }
                    PdfCopySupport.flushCopiedObjects(destination, src)
                }
                for (pageNumber in start..end) destination.getPage(pageNumber).flush()
                start = end + 1
            }
        }
        return (bySource.keys + removals).size
    }

    private fun appendedFrom(page: PdfPage): Int? {
        val base = page.pdfObject.getAsDictionary(EDIT_BASE) ?: return null
        val streams = base.getAsNumber(STREAMS)?.intValue() ?: return null
        if (streams < 0 || page.contentStreamCount <= streams) return null
        var length = 0L
        for (index in 0 until streams) length += page.getContentStream(index).bytes.size
        if (length != base.getAsNumber(LENGTH)?.longValue()) return null
        return streams
    }

    private fun removeContentStreamsFrom(page: PdfPage, streams: Int) {
        val contents = page.pdfObject.getAsArray(PdfName.Contents)
        if (contents == null) {
            if (streams == 0) page.pdfObject.remove(PdfName.Contents)
        } else {
            for (index in contents.size() - 1 downTo streams) contents.remove(index)
        }
        page.pdfObject.setModified()
    }

    private fun layersOf(resources: PdfDictionary?): List<PdfStream> {
        val result = ArrayList<PdfStream>()
        val seen = HashSet<PdfStream>()
        fun visit(dictionary: PdfDictionary?, depth: Int) {
            if (depth >= MAX_DEPTH) return
            val xObjects = dictionary?.getAsDictionary(PdfName.XObject) ?: return
            for (name in xObjects.keySet()) {
                val stream = xObjects.getAsStream(name) ?: continue
                if (!seen.add(stream)) continue
                if (stream.containsKey(LAYER_TAG)) {
                    result += stream
                } else if (stream.getAsName(PdfName.Subtype) == PdfName.Form) {
                    visit(stream.getAsDictionary(PdfName.Resources), depth + 1)
                }
            }
        }
        visit(resources, 0)
        return result
    }

    private fun removeLayer(page: PdfPage) {
        val layer = page.pdfObject.getAsStream(EDIT_FORM) ?: return
        val reference = layer.indirectReference
        page.pdfObject.remove(EDIT_FORM)
        val xObjects = page.pdfObject.getAsDictionary(PdfName.Resources)?.getAsDictionary(PdfName.XObject) ?: return
        val names = xObjects.keySet().filter { name ->
            val stream = xObjects.getAsStream(name)
            stream === layer || (reference != null && stream?.indirectReference == reference)
        }
        for (name in names) xObjects.remove(name)
        val contents = page.pdfObject.getAsArray(PdfName.Contents) ?: return
        for (index in contents.size() - 1 downTo 0) {
            val stream = contents.getAsStream(index) ?: continue
            val text = String(stream.bytes, Charsets.ISO_8859_1)
            if (text.length <= 128 && names.any { text.contains("$it Do") }) contents.remove(index)
        }
    }

    private fun appendEdit(target: PdfPage, plpPage: PdfPage, matrix: DoubleArray) {
        val inverse = inverse(matrix) ?: return
        val base = plpPage.pdfObject.getAsDictionary(EDIT_BASE) ?: return
        val firstNew = base.getAsNumber(STREAMS)?.intValue() ?: return
        val sourceResources = plpPage.resources.pdfObject
        val destination = target.document

        val existing = target.pdfObject.getAsStream(EDIT_FORM)
        val form = existing ?: PdfFormXObject(target.mediaBox).pdfObject.also {
            it.put(PdfName.Resources, PdfDictionary())
            it.put(LAYER_TAG, PdfBoolean.TRUE)
        }
        val formResources = form.getAsDictionary(PdfName.Resources)
            ?: PdfDictionary().also { form.put(PdfName.Resources, it) }

        val chunk = ByteArrayOutputStream()
        chunk.write("q\n".toByteArray(Charsets.ISO_8859_1))
        PdfOutputStream(chunk).use { out ->
            for (value in inverse) out.writeDouble(value, true).writeSpace()
            out.writeString("cm\n")
        }
        for (index in firstNew until plpPage.contentStreamCount) {
            val bytes = plpPage.getContentStream(index).bytes
            chunk.write(renameResources(bytes, sourceResources, formResources, destination))
            chunk.write('\n'.code)
        }
        chunk.write("Q\n".toByteArray(Charsets.ISO_8859_1))

        if (existing != null) {
            val previous = existing.bytes
            existing.remove(PdfName.Filter)
            existing.remove(PdfName.DecodeParms)
            existing.setData(previous + chunk.toByteArray())
        } else {
            form.setData(chunk.toByteArray())
            form.makeIndirect(destination)
            target.pdfObject.put(EDIT_FORM, form)
            val ownResources = PdfDictionary(target.pdfObject.getAsDictionary(PdfName.Resources) ?: PdfDictionary())
            ownResources.put(
                PdfName.XObject,
                PdfDictionary(ownResources.getAsDictionary(PdfName.XObject) ?: PdfDictionary())
            )
            target.pdfObject.put(PdfName.Resources, ownResources)
            PdfCanvas(target.newContentStreamAfter(), PdfResources(ownResources), destination)
                .saveState()
                .addXObjectWithTransformationMatrix(PdfFormXObject(form), 1f, 0f, 0f, 1f, 0f, 0f)
                .restoreState()
        }
    }

    private fun renameResources(
        bytes: ByteArray,
        sourceResources: PdfDictionary,
        formResources: PdfDictionary,
        destination: PdfDocument
    ): ByteArray {
        val renamed = HashMap<Pair<PdfName, PdfName>, PdfName>()
        fun target(category: PdfName, name: PdfName): PdfName? {
            val value = sourceResources.getAsDictionary(category)?.get(name) ?: return null
            return renamed.getOrPut(category to name) {
                val bucket = formResources.getAsDictionary(category)
                    ?: PdfDictionary().also { formResources.put(category, it) }
                var counter = 1
                while (resourceNameUsed(formResources, PdfName("$NAME_PREFIX$counter"))) counter++
                val newName = PdfName("$NAME_PREFIX$counter")
                bucket.put(newName, value.copyTo(destination))
                newName
            }
        }

        val output = ByteArrayOutputStream(bytes.size + 64)
        val tokenizer = PdfTokenizer(RandomAccessFileOrArray(RandomAccessSourceFactory().createSource(bytes)))
        tokenizer.use {
            val parser = PdfCanvasParser(tokenizer, PdfResources(sourceResources))
            val operands = ArrayList<PdfObject>()
            var start = 0
            while (parser.parse(operands).isNotEmpty()) {
                val end = tokenizer.position.toInt()
                val operator = operands.last().toString()
                val replacement = renameIndex(operator, operands)?.let { (index, category) ->
                    (operands[index] as? PdfName)?.let { name -> target(category, name)?.let { index to it } }
                }
                if (replacement == null) {
                    output.write(bytes, start, end - start)
                } else {
                    output.write('\n'.code)
                    PdfOutputStream(output).let { out ->
                        for (index in 0 until operands.size - 1) {
                            out.write(if (index == replacement.first) replacement.second else operands[index])
                            out.writeSpace()
                        }
                        out.writeString(operator)
                        out.flush()
                    }
                }
                start = end
            }
            output.write(bytes, start, bytes.size - start)
        }
        return output.toByteArray()
    }

    private fun renameIndex(operator: String, operands: List<PdfObject>): Pair<Int, PdfName>? = when (operator) {
        "Do" -> 0 to PdfName.XObject
        "gs" -> 0 to PdfName.ExtGState
        "Tf" -> 0 to PdfName.Font
        "cs", "CS" -> 0 to PdfName.ColorSpace
        "sh" -> 0 to PdfName.Shading
        "scn", "SCN" -> if (operands.size >= 2 && operands[operands.size - 2] is PdfName) {
            (operands.size - 2) to PdfName.Pattern
        } else null
        "BDC", "DP" -> if (operands.size >= 3 && operands[1] is PdfName) 1 to PdfName.Properties else null
        else -> null
    }

    private fun resourceNameUsed(resources: PdfDictionary, name: PdfName): Boolean =
        resources.keySet().any { category -> resources.getAsDictionary(category)?.containsKey(name) == true }
}