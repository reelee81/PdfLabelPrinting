package hu.reelee81.pdflabelprinting.pdf

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import hu.reelee81.pdflabelprinting.core.NupConfig
import hu.reelee81.pdflabelprinting.core.PdfCropRect
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfNupBuilderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val a4 = PageSize.A4.width to PageSize.A4.height
    private val toA4Sideways = PageSize.A4.width / PageSize.A4.height

    private class Built(val sizes: List<Pair<Float, Float>>, val cells: List<List<DoubleArray>>)

    @Test
    fun aOneUpPortraitA4PageIsPlacedUnscaled() {
        val built = build(listOf(a4), config(rows = 1, cols = 1))

        assertEquals(listOf(a4), built.sizes)
        assertCell(built.cells[0][0], 0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    }

    @Test
    fun quarterTurnsShrinkASidewaysPageIntoTheCentreAndAHalfTurnFlipsIt() {
        val k = toA4Sideways.toDouble()
        val sidewaysHeight = PageSize.A4.width * toA4Sideways
        val bottom = (PageSize.A4.height - sidewaysHeight) / 2.0
        val built = build(listOf(a4, a4, a4), config(rows = 1, cols = 1), rotation = intArrayOf(1, 2, 3))

        assertEquals(listOf(a4, a4, a4), built.sizes)
        assertCell(built.cells[0][0], 0, 0.0, k, -k, 0.0, PageSize.A4.width.toDouble(), bottom)
        assertCell(built.cells[1][0], 1, -1.0, 0.0, 0.0, -1.0, PageSize.A4.width.toDouble(), PageSize.A4.height.toDouble())
        assertCell(built.cells[2][0], 2, 0.0, -k, k, 0.0, 0.0, bottom + sidewaysHeight)
    }

    @Test
    fun aOneUpLandscapeLayoutShrinksAPortraitPageAndCentresIt() {
        val k = toA4Sideways.toDouble()
        val built = build(listOf(a4), config(rows = 1, cols = 1, landscape = true))

        assertEquals(listOf(PageSize.A4.height to PageSize.A4.width), built.sizes)
        assertCell(built.cells[0][0], 0, k, 0.0, 0.0, k, (PageSize.A4.height - PageSize.A4.width * toA4Sideways) / 2.0, 0.0)
    }

    @Test
    fun aCropMovesTheCroppedOriginToTheCentreOfTheCell() {
        val crop = mapOf(0 to PdfCropRect(0.5f, 0.5f, 1f, 1f))
        val built = build(listOf(a4), config(rows = 1, cols = 1), crop = crop)

        val halfWidth = PageSize.A4.width / 2f
        val halfHeight = PageSize.A4.height / 2f
        val left = (PageSize.A4.width - halfWidth) * 0.5f
        val bottom = (PageSize.A4.height - halfHeight) * 0.5f
        assertCell(built.cells[0][0], 0, 1.0, 0.0, 0.0, 1.0, (left - halfWidth).toDouble(), (bottom - halfHeight).toDouble())
    }

    @Test
    fun aTwoByTwoLayoutWithMarginScalesEachPageIntoItsCellAndHonoursPositions() {
        val pages = List(5) { 200f to 100f }
        val built = build(
            pages,
            config(rows = 2, cols = 2, marginMm = 10, drawFrame = true, scale = true),
            position = intArrayOf(1, 9, 5, 5, 5)
        )

        val margin = 10 * 72f / 25.4f
        val cellW = (PageSize.A4.width - margin * 3) / 2
        val cellH = (PageSize.A4.height - margin * 3) / 2
        val k = cellW / 200f
        val scaledH = 100f * k
        val topRowY = margin + cellH + margin

        assertEquals(listOf(a4, a4), built.sizes)
        assertEquals(listOf(4, 1), built.cells.map { it.size })
        assertCell(built.cells[0][0], 0, k.toDouble(), 0.0, 0.0, k.toDouble(), margin.toDouble(), (topRowY + cellH - scaledH).toDouble())
        assertCell(built.cells[0][1], 1, k.toDouble(), 0.0, 0.0, k.toDouble(), (margin + cellW + margin).toDouble(), topRowY.toDouble())
        assertCell(built.cells[1][0], 4, k.toDouble(), 0.0, 0.0, k.toDouble(), margin.toDouble(), (topRowY + (cellH - scaledH) * 0.5f).toDouble())
    }

    @Test
    fun theBatchSizeDoesNotChangeTheLayout() {
        val pages = List(7) { if (it % 2 == 0) a4 else 300f to 200f }
        val cfg = config(rows = 2, cols = 2, marginMm = 3)
        val rotation = intArrayOf(0, 1, 2, 3, 0, 1, 2)
        val single = build(pages, cfg, rotation = rotation, batchSize = 1)
        val large = build(pages, cfg, rotation = rotation, batchSize = 20)

        assertEquals(single.sizes, large.sizes)
        assertEquals(single.cells.map { it.size }, large.cells.map { it.size })
        single.cells.flatten().zip(large.cells.flatten()).forEach { (a, b) -> assertArrayEquals(a, b, 1e-9) }
    }

    private fun config(
        rows: Int,
        cols: Int,
        marginMm: Int = 0,
        landscape: Boolean = false,
        drawFrame: Boolean = false,
        scale: Boolean = false
    ) = NupConfig(rows, cols, marginMm, landscape, drawFrame, scale, fitIndividually = false)

    private fun build(
        pages: List<Pair<Float, Float>>,
        config: NupConfig,
        rotation: IntArray = IntArray(pages.size),
        position: IntArray = IntArray(pages.size) { 5 },
        crop: Map<Int, PdfCropRect> = emptyMap(),
        batchSize: Int = 20
    ): Built {
        val dir = temp.newFolder()
        val source = File(dir, "source.pdf")
        PdfDocument(PdfWriter(source)).use { pdf ->
            for ((width, height) in pages) {
                val page = pdf.addNewPage(PageSize(width, height))
                PdfCanvas(page).rectangle(10.0, 10.0, (width - 20).toDouble(), (height - 20).toDouble()).fill().release()
            }
        }
        val output = File(dir, "plp.pdf")
        PdfNupBuilder.build(
            source = source,
            totalSrcPages = pages.size,
            outFile = output,
            workDir = dir,
            config = config,
            rotation = rotation,
            position = position,
            crop = crop,
            batchSize = batchSize,
            progressStage = { NoProgress }
        )
        return PdfDocument(PdfReader(output)).use { pdf ->
            val sizes = (1..pdf.numberOfPages).map { pdf.getPage(it).pageSize.let { box -> box.width to box.height } }
            val cells = (1..pdf.numberOfPages).map { number ->
                val values = pdf.getPage(number).pdfObject
                    .getAsDictionary(PdfName.PieceInfo)
                    .getAsDictionary(PdfName("PdfLabelPrinting"))
                    .getAsDictionary(PdfName.Private)
                    .getAsDictionary(PdfName("Base"))
                    .getAsArray(PdfName("Cells"))
                (0 until values.size() / 7).map { cell ->
                    DoubleArray(7) { values.getAsNumber(cell * 7 + it).doubleValue() }
                }
            }
            Built(sizes, cells)
        }
    }

    private fun assertCell(actual: DoubleArray, sourceIndex: Int, vararg matrix: Double) {
        assertEquals(sourceIndex.toDouble(), actual[0], 0.0)
        assertArrayEquals(matrix, actual.copyOfRange(1, 7), 0.01)
    }

    private object NoProgress : PdfProgress {
        override fun advance(units: Int) = Unit
        override fun idle() = Unit
    }
}
