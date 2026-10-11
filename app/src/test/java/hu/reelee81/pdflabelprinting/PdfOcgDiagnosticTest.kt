package hu.reelee81.pdflabelprinting

import com.itextpdf.kernel.pdf.PdfArray
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

class PdfOcgDiagnosticTest {
    @Test
    fun whereDoLayerSettingsGetLost() {
        assumeTrue("Enabled explicitly", System.getenv("PDFLABEL_OCG_DIAG") == "1")
        val folder = listOf(File("Teszt_PDF_ek/03_test_pdfs"), File("../Teszt_PDF_ek/03_test_pdfs")).first { it.isDirectory }
        val outDir = File("build/diagnostics").apply { mkdirs() }
        val report = StringBuilder()
        for (name in listOf("issue269_1", "issue269_2", "issue11144_reduced", "issue12007_reduced", "visibility_expressions")) {
            val source = File(folder, "$name.pdf")
            val work = File(outDir, "${name}_work.pdf")
            val output = File(outDir, "${name}_out.pdf")
            PdfDocument(PdfWriter(work)).use { dest ->
                PdfDocument(PdfReader(source).setMemorySavingMode(true).setUnethicalReading(true)).use { src ->
                    report.appendLine("== $name source OCProperties: ${describe(src)}")
                    src.copyPagesTo(1, src.numberOfPages, dest, PdfVisibleAppearanceCopier(outDir))
                    PdfCopySupport.flushCopiedObjects(dest, src)
                }
            }
            PdfDocument(PdfReader(work)).use { report.appendLine("   work OCProperties:   ${describe(it)}") }
            PdfDocument(PdfWriter(output)).use { pdf ->
                PdfDocument(PdfReader(work)).use { src ->
                    val page = src.getPage(1)
                    val target = pdf.addNewPage(com.itextpdf.kernel.geom.PageSize(page.pageSize))
                    val form = page.copyAsFormXObject(pdf)
                    form.bBox = PdfArray(PdfCopySupport.visibleBox(page))
                    PdfCanvas(target).addXObjectWithTransformationMatrix(form, 1f, 0f, 0f, 1f, 0f, 0f)
                }
            }
            PdfDocument(PdfReader(output)).use { report.appendLine("   output OCProperties: ${describe(it)}") }
        }
        File(outDir, "ocg_diag.txt").writeText(report.toString())
        println(report)
    }

    private fun describe(pdf: PdfDocument): String {
        val properties = pdf.catalog.pdfObject.getAsDictionary(PdfName.OCProperties) ?: return "NINCS"
        val ocgs = properties.getAsArray(PdfName.OCGs)?.size() ?: 0
        val off = properties.getAsDictionary(PdfName.D)?.getAsArray(PdfName.OFF)?.size() ?: 0
        return "OCGs=$ocgs, D/OFF=$off"
    }
}
