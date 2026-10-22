package hu.reelee81.pdflabelprinting.pdf

import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfArray
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import hu.reelee81.pdflabelprinting.core.NupBox
import hu.reelee81.pdflabelprinting.core.NupCellLayout
import hu.reelee81.pdflabelprinting.core.NupConfig
import hu.reelee81.pdflabelprinting.core.NupGeometry.DEFAULT_POSITION
import hu.reelee81.pdflabelprinting.core.PdfCropRect
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object PdfNupBuilder {
    @Throws(Exception::class)
    fun build(
        source: File,
        totalSrcPages: Int,
        outFile: File,
        workDir: File,
        config: NupConfig,
        rotation: IntArray,
        position: IntArray,
        crop: Map<Int, PdfCropRect>,
        batchSize: Int,
        progressStage: (Long) -> PdfProgress,
        warn: (message: String, error: Throwable) -> Unit = { _, _ -> }
    ) {
        val writerProps = PdfIo.writerProperties()
        var fitBounds: PdfMuPdfFitBounds? = null

        try {

            val editGeneration = PdfEditProjection.newGeneration()
            val rows = config.rows
            val cols = config.cols
            val land = config.isLandscape
            val drawFrame = config.drawFrame
            val scaleIndRequested = config.scaleIndividually
            val fitIndRequested = config.fitIndividually
            val basePs = if (land) PageSize.A4.rotate()
            else PageSize.A4

            val per      = (rows * cols).coerceAtLeast(1)
            val isPortraitMode = !land
            val isSingleUp     = (per == 1)

            val totalOutPages = if (totalSrcPages == 0) 0 else (totalSrcPages + per - 1) / per
            if (totalOutPages == 0) {
                FileOutputStream(outFile).use {

                }
                return
            }
            val progress = progressStage(
                totalSrcPages.toLong() + totalOutPages + if (per == 1) totalSrcPages else 0
            )

            val partFiles = mutableListOf<File>()

            var outPageStart = 0
            var chunkIndex = 0
            while (outPageStart < totalOutPages) {

                val outPageEndInclusive = min(totalOutPages - 1, outPageStart + batchSize - 1)

                val srcStart = outPageStart * per
                val srcEnd   = min(totalSrcPages - 1, (outPageEndInclusive + 1) * per - 1)

                val partFile = File((outFile.parentFile ?: workDir), outFile.name + ".p" + chunkIndex.toString())

                if (scaleIndRequested && fitIndRequested) {
                    fitBounds = runCatching { PdfMuPdfFitBounds(source.absolutePath) }
                        .onFailure { warn("MuPDF open failed", it) }
                        .getOrNull()
                }

                PdfDocument(PdfIo.reader(source.absolutePath)).use { src ->
                    PdfDocument(
                        PdfWriter(partFile.absolutePath, writerProps)
                    ).use { pdf ->
                        pdf.setFlushUnusedObjects(true)

                        var idxLocal = 0
                        var globalIdx = srcStart
                        val editCells = ArrayList<PdfEditProjection.Cell>()
                        while (globalIdx <= srcEnd) {
                            val page1Based = globalIdx + 1
                            val sp = src.getPage(page1Based)
                            val pageBox = PdfCopySupport.visibleBox(sp)
                            val sz = crop[globalIdx]?.applyTo(pageBox) ?: pageBox

                            val contentBox =
                                if (scaleIndRequested && fitIndRequested) {
                                    runCatching { fitBounds?.contentBox(globalIdx, sz) }
                                        .onFailure { warn("Fit measurement failed on page $page1Based", it) }
                                        .getOrNull()
                                } else {
                                    null
                                }

                            if (idxLocal % per == 0) {
                                val psForThisPage =
                                    if (isPortraitMode && isSingleUp) PageSize.A4
                                    else basePs
                                pdf.addNewPage(psForThisPage)
                            }

                            val currentPage = pdf.lastPage
                            val canvas      = PdfCanvas(currentPage)

                            val psForThisPage = currentPage.pageSize
                            val cell = NupCellLayout.place(
                                config = config,
                                pageWidth = psForThisPage.width,
                                pageHeight = psForThisPage.height,
                                slot = idxLocal,
                                pageBox = pageBox.toNupBox(),
                                cropBox = sz.toNupBox(),
                                contentBox = contentBox?.toNupBox(),
                                quarterTurns = rotation.getOrElse(globalIdx) { 0 },
                                position = position.getOrElse(globalIdx) { DEFAULT_POSITION }
                            )

                            val fx  = runCatching { PdfCopySupport.flattenedFormXObject(sp, pdf, sz) }.getOrNull()
                                ?: sp.copyAsFormXObject(pdf)
                            fx.bBox = PdfArray(sz)
                            runCatching { PdfEditProjection.tagLayerSource(fx, sp, globalIdx, editGeneration) }
                            canvas.saveState()

                            val m = cell.matrix
                            PdfCopySupport.concatPlacementMatrix(canvas, m[0], m[1], m[2], m[3], m[4], m[5])
                            editCells += PdfEditProjection.Cell(globalIdx, m)

                            canvas.addXObjectWithTransformationMatrix(fx, 1f, 0f, 0f, 1f, 0f, 0f)
                            runCatching { fx.flush() }
                            canvas.restoreState()

                            if (drawFrame) {
                                canvas.setStrokeColor(ColorConstants.BLACK)
                                    .setLineWidth(1f)
                                    .rectangle(cell.x.toDouble(), cell.y.toDouble(), cell.width.toDouble(), cell.height.toDouble())
                                    .stroke()
                            }

                            if (idxLocal % per == per - 1 || globalIdx == srcEnd) {
                                runCatching { PdfEditProjection.markPlpPage(currentPage, editCells, editGeneration) }
                                editCells.clear()
                                runCatching { currentPage.flush() }
                            }

                            idxLocal++
                            globalIdx++
                            progress.advance(1)
                        }
                    }
                }

                fitBounds?.close()
                fitBounds = null

                partFiles += partFile
                outPageStart = outPageEndInclusive + 1
                chunkIndex++
            }

            PdfDocument(
                PdfWriter(outFile.absolutePath, writerProps)
            ).use { destDoc ->
                destDoc.setFlushUnusedObjects(true)

                for (pf in partFiles) {
                    val total = PdfDocument(PdfIo.reader(pf.absolutePath)).use { it.numberOfPages }

                    var from = 1
                    while (from <= total) {
                        val to = min(from + batchSize - 1, total)

                        PdfDocument(PdfIo.reader(pf.absolutePath)).use { partDoc ->
                            partDoc.copyPagesTo(from, to, destDoc)
                            runCatching { PdfCopySupport.flushCopiedObjects(destDoc, partDoc) }
                        }

                        val count = to - from + 1
                        for (i in 0 until count) {
                            val p = destDoc.numberOfPages - i
                            if (p >= 1) runCatching { destDoc.getPage(p).flush() }
                        }
                        progress.advance(count)

                        from = to + 1
                    }
                }
                progress.idle()
            }

            partFiles.forEach { runCatching { it.delete() } }

        } finally {
            fitBounds?.let { runCatching { it.close() } }
        }
    }

    private fun Rectangle.toNupBox(): NupBox = NupBox(x, y, width, height)
}
