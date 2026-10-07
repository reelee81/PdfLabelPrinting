package hu.reelee81.pdflabelprinting

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfArray
import com.itextpdf.kernel.pdf.PdfDictionary
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.PdfString
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfCopySupportTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun validOffsetBoxesAreNotRewritten() {
        PdfDocument(PdfWriter(ByteArrayOutputStream())).use { pdf ->
            val page = pdf.addNewPage(PageSize(600f, 800f))
            val crop = Rectangle(97f, 607f, 370f, 141f)
            page.setCropBox(crop)
            PdfCopySupport.repairInvalidBoxes(page)
            assertEquals(crop.toString(), page.cropBox.toString())
            assertEquals(crop.toString(), PdfCopySupport.visibleBox(page).toString())
        }
    }

    @Test
    fun emptyAndDisjointBoxesHaveFiniteNonzeroFallbacks() {
        PdfDocument(PdfWriter(ByteArrayOutputStream())).use { pdf ->
            for (crop in listOf(Rectangle(0f, 0f), Rectangle(900f, 900f, 50f, 50f))) {
                val page = pdf.addNewPage()
                page.setCropBox(crop)
                PdfCopySupport.repairInvalidBoxes(page)
                assertEquals(page.pageSize.toString(), PdfCopySupport.visibleBox(page).toString())
            }
            val page = pdf.addNewPage()
            page.setMediaBox(Rectangle(0f, 0f)).setCropBox(Rectangle(0f, 0f))
            PdfCopySupport.repairInvalidBoxes(page)
            assertEquals(PageSize.LETTER.toString(), PdfCopySupport.visibleBox(page).toString())
        }
    }

    @Test
    fun nativeXObjectPlacementDoesNotTranslateAnOffsetCropTwice() {
        val input = temporary.newFile("offset.pdf")
        PdfDocument(PdfWriter(input.absolutePath)).use { pdf ->
            val page = pdf.addNewPage()
            page.setCropBox(Rectangle(100f, 200f, 200f, 100f))
            PdfCanvas(page).beginText().setFontAndSize(PdfFontFactory.createFont(), 10f)
                .moveText(110.0, 220.0).showText("VISIBLE").endText()
        }
        val output = temporary.newFile("placed.pdf")
        PdfDocument(PdfReader(input.absolutePath)).use { source ->
            PdfDocument(PdfWriter(output.absolutePath)).use { destination ->
                val page = source.getPage(1)
                val box = PdfCopySupport.visibleBox(page)
                val form = page.copyAsFormXObject(destination)
                PdfCanvas(destination.addNewPage(PageSize(200f, 100f)))
                    .concatMatrix(1.0, 0.0, 0.0, 1.0, -box.x.toDouble(), -box.y.toDouble())
                    .addXObjectWithTransformationMatrix(form, 1f, 0f, 0f, 1f, 0f, 0f)
            }
        }
        PdfDocument(PdfReader(output.absolutePath)).use { result ->
            assertTrue(PdfTextExtractor.getTextFromPage(result.getPage(1)).contains("VISIBLE"))
        }
    }

    @Test
    fun layeredFixtureClosesAfterCopiedObjectFlush() {
        val file = fixture("bug1737260.pdf")
        val output = temporary.newFile("layers.pdf")
        PdfDocument(PdfReader(file.absolutePath).setMemorySavingMode(true)).use { source ->
            PdfDocument(PdfWriter(output.absolutePath)).use { destination ->
                source.copyPagesTo(1, 1, destination, PdfVisibleAppearanceCopier(temporary.root))
                PdfCopySupport.flushCopiedObjects(destination, source)
            }
        }
        PdfDocument(PdfReader(output.absolutePath)).use { result ->
            assertEquals(1, result.numberOfPages)
            assertEquals(2, result.catalog.pdfObject.getAsDictionary(PdfName.OCProperties).getAsArray(PdfName.OCGs).size())
        }
    }

    @Test
    fun placementMatrixDoesNotRoundScalingToTwoDecimalPlaces() {
        PdfDocument(PdfWriter(ByteArrayOutputStream())).use { pdf ->
            val page = pdf.addNewPage()
            val canvas = PdfCanvas(page)
            val scale = 1.1970702
            PdfCopySupport.concatPlacementMatrix(canvas, scale, 0.0, 0.0, scale, -65.220123, -116.980123)
            val content = page.contentBytes.toString(Charsets.US_ASCII)
            assertTrue(content.contains("1.19707 0 0 1.19707 -65.220123 -116.980123 cm"))
            assertEquals(scale.toFloat(), canvas.graphicsState.ctm[0], 0.0000001f)
        }
    }

    @Test
    fun mergedPushButtonFixtureCanBeImported() {
        val file = fixture("160F-2019.pdf")
        val output = temporary.newFile("buttons.pdf")
        PdfDocument(PdfWriter(output.absolutePath)).use { destination ->
            val count = PdfDocument(PdfReader(file.absolutePath)).use { it.numberOfPages }
            for (index in 1..count) {
                PdfDocument(PdfReader(file.absolutePath).setMemorySavingMode(true)).use { source ->
                    source.copyPagesTo(index, index, destination, PdfVisibleAppearanceCopier(temporary.root))
                    PdfCopySupport.flushCopiedObjects(destination, source)
                }
                destination.lastPage.flush()
            }
        }
        PdfDocument(PdfReader(output.absolutePath)).use { result ->
            assertTrue(result.numberOfPages > 0)
            assertTrue(result.getPage(1).annotations.isEmpty())
        }
        assertTrue(temporary.root.listFiles().orEmpty().none { it.name.startsWith("pdf_widget_appearance_") })
    }

    @Test
    fun malformedIdentityCmapIsRepairedWithoutReplacingTheEmbeddedFont() {
        val output = temporary.newFile("encoding.pdf")
        var originalFont: ByteArray? = null
        PdfDocument(PdfReader(fixture("bug920426.pdf").absolutePath)).use { source ->
            val font = source.getPage(1).resources.pdfObject.getAsDictionary(PdfName.Font).getAsDictionary(PdfName("F1"))
            originalFont = font.getAsArray(PdfName.DescendantFonts).getAsDictionary(0)
                .getAsDictionary(PdfName.FontDescriptor).getAsStream(PdfName.FontFile2).bytes
            PdfDocument(PdfWriter(output.absolutePath)).use { destination ->
                source.copyPagesTo(1, 1, destination, PdfVisibleAppearanceCopier(temporary.root))
                PdfCopySupport.flushCopiedObjects(destination, source)
            }
        }
        PdfDocument(PdfReader(output.absolutePath)).use { result ->
            val font = result.getPage(1).resources.pdfObject.getAsDictionary(PdfName.Font).getAsDictionary(PdfName("F1"))
            val program = font.getAsArray(PdfName.DescendantFonts).getAsDictionary(0)
                .getAsDictionary(PdfName.FontDescriptor).getAsStream(PdfName.FontFile2).bytes
            assertArrayEquals(requireNotNull(originalFont), program)
            val encoding = font.getAsStream(PdfName.Encoding).bytes.toString(Charsets.US_ASCII)
            assertEquals(PdfName("PdfLabelIdentityEncoding"), font.getAsStream(PdfName.Encoding).getAsName(PdfName.CMapName))
            assertTrue(encoding.contains("begincidchar"))
            assertTrue(encoding.contains("<0043> 38"))
            val unicode = font.getAsStream(PdfName.ToUnicode).bytes.toString(Charsets.US_ASCII)
            assertEquals(PdfName("PdfLabelIdentityUnicode"), font.getAsStream(PdfName.ToUnicode).getAsName(PdfName.CMapName))
            assertTrue(unicode.contains("<0043> <0043>"))
            assertTrue(!unicode.contains("beginbfrange"))
            assertTrue(PdfTextExtractor.getTextFromPage(result.getPage(1)).contains("Checkliste Service"))
        }
    }

    @Test
    fun excessivelyNestedMalformedCmapIsLeftUnchanged() {
        PdfDocument(PdfWriter(ByteArrayOutputStream())).use { pdf ->
            val font = PdfDictionary().apply {
                put(PdfName.Subtype, PdfName.Type0)
                put(PdfName.ToUnicode, PdfName("Identity-H"))
                put(PdfName.Encoding, PdfStream("[".repeat(1000).toByteArray(Charsets.US_ASCII)))
                put(PdfName.DescendantFonts, PdfArray(PdfDictionary().apply {
                    put(PdfName.Subtype, PdfName.CIDFontType2)
                    put(PdfName.CIDSystemInfo, PdfDictionary().apply { put(PdfName.Ordering, PdfString("Identity")) })
                }))
            }
            val page = pdf.addNewPage()
            page.resources.pdfObject.put(PdfName.Font, PdfDictionary().apply { put(PdfName("F1"), font) })
            PdfIdentityCMapRepair.repair(page)
            assertEquals(PdfName("Identity-H"), font.getAsName(PdfName.ToUnicode))
        }
    }

    private fun fixture(name: String): File {
        val file = listOf(File("Teszt_PDF_ek/03_test_pdfs/$name"), File("../Teszt_PDF_ek/03_test_pdfs/$name"))
            .firstOrNull { it.isFile }
        assumeTrue("Local PDF review fixtures are not present", file != null)
        return requireNotNull(file)
    }
}
