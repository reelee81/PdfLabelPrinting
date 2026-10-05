package hu.reelee81.pdflabelprinting

import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfArray
import com.itextpdf.kernel.pdf.PdfDictionary
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfNumber
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.PdfString
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.annot.PdfAnnotation
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfVisibleAppearanceCopierTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun normalPageContentBoxesAndRotationAreUnchanged() {
        val original = makePdf { pdf ->
            pdf.getPage(1).setCropBox(Rectangle(10f, 20f, 300f, 400f)).setRotation(90)
        }
        PdfDocument(PdfReader(ByteArrayInputStream(original))).use { source ->
            PdfDocument(PdfReader(ByteArrayInputStream(import(original)))).use { result ->
                assertArrayEquals(source.getPage(1).contentBytes, result.getPage(1).contentBytes)
                assertEquals(source.getPage(1).cropBox.toString(), result.getPage(1).cropBox.toString())
                assertEquals(90, result.getPage(1).rotation)
            }
        }
    }

    @Test
    fun existingFormAndSignatureAppearancesSurviveXObjectCopyWithoutRasterization() {
        val original = makePdf { pdf ->
            addWidget(pdf, PdfName.Tx, "FORM VALUE")
            addWidget(pdf, PdfName.Sig, "SIGNATURE BADGE", 100f)
        }
        PdfDocument(PdfReader(ByteArrayInputStream(import(original, asXObject = true)))).use { result ->
            val text = PdfTextExtractor.getTextFromPage(result.getPage(1))
            assertTrue(text.contains("FORM VALUE"))
            assertTrue(text.contains("SIGNATURE BADGE"))
            assertTrue(result.getPage(1).annotations.isEmpty())
            assertFalse(result.catalog.pdfObject.containsKey(PdfName.AcroForm))
            assertOnlyFormXObjects(result.getPage(1).resources.pdfObject)
        }
    }

    @Test
    fun inheritedFormValueAndDocumentDefaultFontAreRegeneratedOnlyForMissingAppearance() {
        val original = makePdf { pdf ->
            val font = pdf.addFont(PdfFontFactory.createFont())
            val fonts = PdfDictionary().apply { put(PdfName("Helv"), font.pdfObject) }
            val form = PdfDictionary().apply {
                put(PdfName.Fields, PdfArray())
                put(PdfName.DA, PdfString("/Helv 12 Tf 0 g"))
                put(PdfName.DR, PdfDictionary().apply { put(PdfName.Font, fonts) })
            }
            pdf.catalog.pdfObject.put(PdfName.AcroForm, form)
            val parent = PdfDictionary().apply {
                put(PdfName.FT, PdfName.Tx)
                put(PdfName.V, PdfString("INHERITED VALUE"))
                put(PdfName.T, PdfString("parent"))
                put(PdfName.Kids, PdfArray())
                makeIndirect(pdf)
            }
            val widget = baseWidget(40f).apply { put(PdfName.Parent, parent) }
            pdf.getPage(1).addAnnotation(PdfAnnotation.makeAnnotation(widget))
            parent.getAsArray(PdfName.Kids).add(widget)
            form.getAsArray(PdfName.Fields).add(parent)
        }
        val result = import(original, asXObject = true)
        PdfDocument(PdfReader(ByteArrayInputStream(result))).use { pdf ->
            assertTrue(PdfTextExtractor.getTextFromPage(pdf.getPage(1)).contains("INHERITED VALUE"))
            assertOnlyFormXObjects(pdf.getPage(1).resources.pdfObject)
        }
        assertTrue(temporary.root.listFiles().orEmpty().none { it.name.startsWith("pdf_widget_appearance_") })
    }

    @Test
    fun checkboxOffDoesNotUseAnAvailableOnAppearance() {
        val original = makePdf { pdf ->
            val widget = baseWidget(40f).apply {
                put(PdfName.FT, PdfName.Btn)
                put(PdfName.V, PdfName("Off"))
                put(PdfName.AS, PdfName("Off"))
                put(PdfName.T, PdfString("checkbox"))
                put(PdfName.AP, PdfDictionary().apply {
                    put(PdfName.N, PdfDictionary().apply { put(PdfName("Yes"), appearance(pdf, "MUST NOT APPEAR")) })
                })
            }
            pdf.getPage(1).addAnnotation(PdfAnnotation.makeAnnotation(widget))
        }
        PdfDocument(PdfReader(ByteArrayInputStream(import(original)))).use { pdf ->
            assertFalse(PdfTextExtractor.getTextFromPage(pdf.getPage(1)).contains("MUST NOT APPEAR"))
        }
    }

    @Test
    fun explicitEmptyAppearanceIsNotRegenerated() {
        val original = makePdf { pdf ->
            val widget = baseWidget(40f).apply {
                put(PdfName.FT, PdfName.Tx)
                put(PdfName.V, PdfString("INTENTIONALLY INVISIBLE"))
                put(PdfName.AP, PdfDictionary().apply {
                    put(PdfName.N, PdfFormXObject(Rectangle(0f, 0f)).pdfObject)
                })
            }
            pdf.getPage(1).addAnnotation(PdfAnnotation.makeAnnotation(widget))
        }
        PdfDocument(PdfReader(ByteArrayInputStream(import(original)))).use { pdf ->
            assertFalse(PdfTextExtractor.getTextFromPage(pdf.getPage(1)).contains("INTENTIONALLY INVISIBLE"))
        }
    }

    @Test
    fun commentsPopupsAndHiddenAppearancesAreNotPainted() {
        val original = makePdf { pdf ->
            for ((subtype, flags) in listOf(PdfName.Text to 0, PdfName.Popup to 0, PdfName.Widget to 2)) {
                val annotation = baseWidget(40f).apply {
                    put(PdfName.Subtype, subtype)
                    put(PdfName.F, PdfNumber(flags))
                    put(PdfName.AP, PdfDictionary().apply { put(PdfName.N, appearance(pdf, "HIDDEN")) })
                }
                pdf.getPage(1).addAnnotation(PdfAnnotation.makeAnnotation(annotation))
            }
        }
        PdfDocument(PdfReader(ByteArrayInputStream(import(original)))).use { pdf ->
            assertEquals("BODY", PdfTextExtractor.getTextFromPage(pdf.getPage(1)).trim())
            assertTrue(pdf.getPage(1).annotations.isEmpty())
        }
    }

    @Test
    fun allMarkupQuadsAreDrawnAboveContentWithMultiplyBlend() {
        val original = makePdf { pdf ->
            val annotation = baseWidget(40f).apply {
                put(PdfName.Subtype, PdfName.Highlight)
                put(PdfName.QuadPoints, PdfArray(floatArrayOf(
                    20f, 80f, 120f, 80f, 20f, 60f, 120f, 60f,
                    20f, 40f, 120f, 40f, 20f, 20f, 120f, 20f
                )))
            }
            pdf.getPage(1).addAnnotation(PdfAnnotation.makeAnnotation(annotation))
        }
        PdfDocument(PdfReader(ByteArrayInputStream(import(original)))).use { pdf ->
            val page = pdf.getPage(1)
            val content = page.contentBytes.toString(Charsets.US_ASCII)
            assertTrue(content.contains("20 80 m"))
            assertTrue(content.contains("20 40 m"))
            val states = page.resources.pdfObject.getAsDictionary(PdfName.ExtGState)
            assertEquals(PdfName.Multiply, states.getAsDictionary(states.keySet().first()).getAsName(PdfName.BM))
        }
    }

    @Test
    fun importingStaticWorkingCopyDoesNotAddAnotherAppearanceLayer() {
        val original = makePdf { pdf -> addWidget(pdf, PdfName.Tx, "ONLY ONCE") }
        val first = import(original)
        val second = import(first)
        PdfDocument(PdfReader(ByteArrayInputStream(first))).use { a ->
            PdfDocument(PdfReader(ByteArrayInputStream(second))).use { b ->
                assertArrayEquals(a.getPage(1).contentBytes, b.getPage(1).contentBytes)
            }
        }
    }

    @Test
    fun radioOnStateIsGeneratedWhenItHasNoAppearance() {
        val original = makePdf { pdf ->
            val widget = baseWidget(40f).apply {
                put(PdfName.FT, PdfName.Btn)
                put(PdfName.Ff, PdfNumber(1 shl 15))
                put(PdfName.V, PdfName("Selected"))
                put(PdfName.AS, PdfName("Selected"))
            }
            pdf.getPage(1).addAnnotation(PdfAnnotation.makeAnnotation(widget))
        }
        PdfDocument(PdfReader(ByteArrayInputStream(import(original)))).use { pdf ->
            assertNotNull(pdf.getPage(1).resources.pdfObject.getAsDictionary(PdfName.XObject))
        }
    }

    @Test
    fun appearanceMatrixAndOffsetBoundsKeepNativeCoordinatesOnRotatedPages() {
        val original = makePdf { pdf ->
            pdf.getPage(1).setRotation(270).setCropBox(Rectangle(10f, 20f, 400f, 500f))
            val form = PdfFormXObject(Rectangle(10f, 20f, 200f, 30f))
            form.put(PdfName.Matrix, PdfArray(floatArrayOf(1f, 0f, 0f, 1f, 7f, 11f)))
            PdfCanvas(form, pdf).beginText().setFontAndSize(PdfFontFactory.createFont(), 10f)
                .moveText(13.0, 30.0).showText("POSITION").endText()
            val widget = baseWidget(40f).apply {
                put(PdfName.FT, PdfName.Tx)
                put(PdfName.AP, PdfDictionary().apply { put(PdfName.N, form.pdfObject) })
            }
            pdf.getPage(1).addAnnotation(PdfAnnotation.makeAnnotation(widget))
        }
        PdfDocument(PdfReader(ByteArrayInputStream(import(original)))).use { pdf ->
            var found = false
            val listener = object : IEventListener {
                override fun eventOccurred(data: IEventData?, type: EventType?) {
                    if (data is TextRenderInfo && data.text == "POSITION") {
                        val position = data.baseline.startPoint
                        assertEquals(33f, position.get(0), 0.001f)
                        assertEquals(50f, position.get(1), 0.001f)
                        found = true
                    }
                }
                override fun getSupportedEvents(): Set<EventType> = setOf(EventType.RENDER_TEXT)
            }
            PdfCanvasProcessor(listener).processPageContent(pdf.getPage(1))
            assertTrue(found)
            assertEquals(270, pdf.getPage(1).rotation)
        }
    }

    @Test
    fun stressMixedPagesKeepTheExistingReopenAndFlushPattern() {
        val count = System.getProperty("pdflabel.stressPages")?.toIntOrNull() ?: 0
        assumeTrue("Enabled explicitly for bounded-heap stress verification", count > 0)
        val sourceFile = temporary.newFile("many-pages.pdf")
        PdfDocument(PdfWriter(sourceFile.absolutePath)).use { pdf ->
            val font = pdf.addFont(PdfFontFactory.createFont())
            val shared = appearance(pdf, "VISIBLE BADGE")
            val defaults = PdfDictionary().apply {
                put(PdfName.Fields, PdfArray())
                put(PdfName.DA, PdfString("/Helv 12 Tf 0 g"))
                put(PdfName.DR, PdfDictionary().apply {
                    put(PdfName.Font, PdfDictionary().apply { put(PdfName("Helv"), font.pdfObject) })
                })
            }
            pdf.catalog.pdfObject.put(PdfName.AcroForm, defaults)
            for (index in 1..count) {
                val page = pdf.addNewPage()
                PdfCanvas(page).beginText().setFontAndSize(font, 12f)
                    .moveText(20.0, 700.0).showText("PAGE $index").endText()
                when (index % 10) {
                    0 -> {
                        val widget = baseWidget(40f).apply {
                            put(PdfName.FT, PdfName.Tx)
                            put(PdfName.T, PdfString("value$index"))
                            put(PdfName.V, PdfString("VALUE $index"))
                        }
                        page.addAnnotation(PdfAnnotation.makeAnnotation(widget))
                        defaults.getAsArray(PdfName.Fields).add(widget)
                    }
                    1 -> {
                        val widget = baseWidget(40f).apply {
                            put(PdfName.FT, PdfName.Sig)
                            put(PdfName.AP, PdfDictionary().apply { put(PdfName.N, shared) })
                        }
                        page.addAnnotation(PdfAnnotation.makeAnnotation(widget))
                    }
                    2 -> {
                        val markup = baseWidget(40f).apply { put(PdfName.Subtype, PdfName.Highlight) }
                        page.addAnnotation(PdfAnnotation.makeAnnotation(markup))
                    }
                }
                page.flush()
            }
        }
        val destinationFile = temporary.newFile("imported.pdf")
        var peakHeap = 0L
        PdfDocument(PdfWriter(destinationFile.absolutePath)).use { destination ->
            destination.setFlushUnusedObjects(true)
            val batch = (System.getProperty("pdflabel.stressBatch")?.toIntOrNull() ?: 10).coerceAtLeast(1)
            for (start in 1..count step batch) {
                val end = minOf(count, start + batch - 1)
                PdfDocument(PdfReader(sourceFile.absolutePath).setMemorySavingMode(true)).use { source ->
                    source.copyPagesTo(start, end, destination, PdfVisibleAppearanceCopier(temporary.root))
                    destination.flushCopiedObjects(source)
                }
                for (page in start..end) destination.getPage(page).flush()
                val runtime = Runtime.getRuntime()
                peakHeap = maxOf(peakHeap, runtime.totalMemory() - runtime.freeMemory())
            }
        }
        PdfDocument(PdfReader(destinationFile.absolutePath).setMemorySavingMode(true)).use { result ->
            assertEquals(count, result.numberOfPages)
            assertTrue(PdfTextExtractor.getTextFromPage(result.getPage(count)).contains("VALUE $count"))
            assertTrue(result.getPage(count).annotations.isEmpty())
        }
        val chunk = temporary.newFile("xobject-chunk.pdf")
        val batch = (System.getProperty("pdflabel.stressBatch")?.toIntOrNull() ?: 10).coerceAtLeast(1)
        for (start in 1..count step batch) {
            val end = minOf(count, start + batch - 1)
            PdfDocument(PdfReader(destinationFile.absolutePath).setMemorySavingMode(true)).use { source ->
                PdfDocument(PdfWriter(chunk.absolutePath)).use { output ->
                    output.setFlushUnusedObjects(true)
                    for (index in start..end) {
                        val target = output.addNewPage()
                        val form = source.getPage(index).copyAsFormXObject(output)
                        PdfCanvas(target).addXObjectAt(form, 0f, 0f)
                        form.flush()
                        target.flush()
                    }
                }
            }
            PdfDocument(PdfReader(chunk.absolutePath).setMemorySavingMode(true)).use { result ->
                val text = PdfTextExtractor.getTextFromPage(result.lastPage)
                assertTrue(text.contains("PAGE $end"))
                if (end % 10 == 0) assertTrue(text.contains("VALUE $end"))
            }
            val runtime = Runtime.getRuntime()
            peakHeap = maxOf(peakHeap, runtime.totalMemory() - runtime.freeMemory())
        }
        println("Mixed PDF stress: pages=$count, peak sampled heap=$peakHeap, heap limit=${Runtime.getRuntime().maxMemory()}")
    }

    @Test
    fun suppliedMixedPdfsCanBeImportedWithReopenedSinglePageBatches() {
        val folder = listOf(File("Urlapmezos_anotacios_teszt_pdf_ek"), File("../Urlapmezos_anotacios_teszt_pdf_ek"))
            .firstOrNull { it.isDirectory }
        assumeTrue("Local PDF review fixtures are not present", folder != null)
        val outputFolder = File("build/appearance-review").apply { mkdirs() }
        val combined = ByteArrayOutputStream()
        var totalPages = 0
        PdfDocument(PdfWriter(combined)).use { destination ->
            destination.setFlushUnusedObjects(true)
            for (file in folder!!.listFiles().orEmpty().filter { it.extension == "pdf" }.sortedBy { it.name }) {
                val pageCount = PdfDocument(PdfReader(file.absolutePath)).use { it.numberOfPages }
                val perFile = ByteArrayOutputStream()
                PdfDocument(PdfWriter(perFile)).use { document ->
                    document.setFlushUnusedObjects(true)
                    for (page in 1..pageCount) {
                        PdfDocument(PdfReader(file.absolutePath).setMemorySavingMode(true)).use { source ->
                            source.copyPagesTo(page, page, destination, PdfVisibleAppearanceCopier(temporary.root))
                            source.copyPagesTo(page, page, document, PdfVisibleAppearanceCopier(temporary.root))
                            destination.flushCopiedObjects(source)
                            document.flushCopiedObjects(source)
                        }
                        destination.lastPage.flush()
                        document.lastPage.flush()
                    }
                }
                val result = import(perFile.toByteArray(), asXObject = true)
                Files.write(File(outputFolder, file.name).toPath(), result)
                totalPages += pageCount
            }
        }
        PdfDocument(PdfReader(ByteArrayInputStream(combined.toByteArray()))).use { result ->
            assertEquals(totalPages, result.numberOfPages)
            for (page in 1..result.numberOfPages) assertTrue(result.getPage(page).annotations.isEmpty())
        }
    }

    private fun import(original: ByteArray, asXObject: Boolean = false): ByteArray {
        val bytes = ByteArrayOutputStream()
        PdfDocument(PdfReader(ByteArrayInputStream(original)).setMemorySavingMode(true)).use { source ->
            PdfDocument(PdfWriter(bytes)).use { destination ->
                source.copyPagesTo(1, source.numberOfPages, destination, PdfVisibleAppearanceCopier(temporary.root))
                destination.flushCopiedObjects(source)
            }
        }
        if (!asXObject) return bytes.toByteArray()
        val output = ByteArrayOutputStream()
        PdfDocument(PdfReader(ByteArrayInputStream(bytes.toByteArray()))).use { source ->
            PdfDocument(PdfWriter(output)).use { destination ->
                for (index in 1..source.numberOfPages) {
                    val page = source.getPage(index)
                    val target = destination.addNewPage(PageSize(page.pageSize))
                    PdfCanvas(target).addXObjectAt(page.copyAsFormXObject(destination), 0f, 0f)
                }
            }
        }
        return output.toByteArray()
    }

    private fun makePdf(block: (PdfDocument) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        PdfDocument(PdfWriter(bytes)).use { pdf ->
            val page = pdf.addNewPage()
            PdfCanvas(page).beginText().setFontAndSize(PdfFontFactory.createFont(), 12f)
                .moveText(20.0, 700.0).showText("BODY").endText()
            block(pdf)
        }
        return bytes.toByteArray()
    }

    private fun baseWidget(y: Float): PdfDictionary = PdfDictionary().apply {
        put(PdfName.Type, PdfName.Annot)
        put(PdfName.Subtype, PdfName.Widget)
        put(PdfName.Rect, PdfArray(Rectangle(30f, y, 200f, 30f)))
    }

    private fun addWidget(pdf: PdfDocument, type: PdfName, text: String, y: Float = 40f) {
        val widget = baseWidget(y).apply {
            put(PdfName.FT, type)
            put(PdfName.T, PdfString(text))
            put(PdfName.AP, PdfDictionary().apply { put(PdfName.N, appearance(pdf, text)) })
        }
        pdf.getPage(1).addAnnotation(PdfAnnotation.makeAnnotation(widget))
    }

    private fun appearance(pdf: PdfDocument, text: String): PdfStream {
        val form = PdfFormXObject(Rectangle(0f, 0f, 200f, 30f))
        PdfCanvas(form, pdf).beginText().setFontAndSize(PdfFontFactory.createFont(), 10f)
            .moveText(3.0, 10.0).showText(text).endText()
        return form.pdfObject
    }

    private fun assertOnlyFormXObjects(resources: PdfDictionary) {
        val objects = resources.getAsDictionary(PdfName.XObject)
        assertNotNull(objects)
        for (key in objects.keySet()) {
            val stream = objects.getAsStream(key)
            assertEquals(PdfName.Form, stream.getAsName(PdfName.Subtype))
            stream.getAsDictionary(PdfName.Resources)?.let {
                if (it.containsKey(PdfName.XObject)) assertOnlyFormXObjects(it)
            }
        }
    }
}
