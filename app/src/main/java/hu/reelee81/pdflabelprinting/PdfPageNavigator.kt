package hu.reelee81.pdflabelprinting

import android.graphics.RectF
import android.os.Bundle
import android.util.SizeF
import android.util.SparseArray
import android.view.View
import androidx.pdf.PdfDocument
import androidx.pdf.PdfPoint
import androidx.pdf.view.PdfView

internal class PdfPageNavigator(private val onOperationStateChanged: () -> Unit) {
    private val state = PdfPageNavigationState()
    private var pdfView: PdfView? = null
    private var loadedDocument: PdfDocument? = null
    private var loading = true
    private var viewStateRestored = false
    private var workPosted = false
    private val visiblePageSizes = SparseArray<SizeF>()

    val currentPageIndex: Int
        get() = state.currentPageIndex
    val isInProgress: Boolean
        get() = loading || state.pendingPageIndex != null

    private val work = Runnable {
        workPosted = false
        applyPendingPage()
    }

    private val layoutListener = View.OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
        if (r - l != oldR - oldL || b - t != oldB - oldT) {
            state.invalidateScroll()
        }
        scheduleWork()
    }

    private val viewportListener = object : PdfView.OnViewportChangedListener {
        override fun onViewportChanged(
            firstVisiblePage: Int,
            visiblePagesCount: Int,
            pageLocations: SparseArray<RectF>,
            zoomLevel: Float
        ) {
            val view = pdfView ?: return
            if (view.pdfDocument !== loadedDocument || loadedDocument == null) return

            var largestArea = 0f
            var dominantPage: Int? = null
            visiblePageSizes.clear()
            for (pageIndex in firstVisiblePage until firstVisiblePage + visiblePagesCount) {
                val bounds = pageLocations[pageIndex] ?: continue
                visiblePageSizes.put(pageIndex, SizeF(bounds.width() / zoomLevel, bounds.height() / zoomLevel))
                val width = (minOf(bounds.right, view.width.toFloat()) - maxOf(bounds.left, 0f))
                    .coerceAtLeast(0f)
                val height = (minOf(bounds.bottom, view.height.toFloat()) - maxOf(bounds.top, 0f))
                    .coerceAtLeast(0f)
                val area = width * height
                if (area > largestArea) {
                    largestArea = area
                    dominantPage = pageIndex
                }
            }
            dominantPage?.let(state::observeVisiblePage)

            if (isInProgress) scheduleWork()
        }
    }

    fun restore(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        val pending = if (savedInstanceState.containsKey(STATE_PENDING_PAGE)) {
            savedInstanceState.getInt(STATE_PENDING_PAGE)
        } else null
        state.restore(savedInstanceState.getInt(STATE_VISIBLE_PAGE, 0), pending)
    }

    fun save(outState: Bundle) {
        outState.putInt(STATE_VISIBLE_PAGE, state.visiblePageIndex)
        state.pendingPageIndex?.let { outState.putInt(STATE_PENDING_PAGE, it) }
    }

    fun bind(view: PdfView) {
        pdfView = view
        viewStateRestored = false
        view.addOnViewportChangedListener(viewportListener)
        view.addOnLayoutChangeListener(layoutListener)
    }

    fun onViewStateRestored() {
        viewStateRestored = true
        scheduleWork()
    }

    fun beginDocumentLoad() {
        loadedDocument = null
        loading = true
        visiblePageSizes.clear()
        state.invalidateScroll()
        onOperationStateChanged()
    }

    fun onDocumentLoaded(document: PdfDocument) {
        if (loadedDocument !== document) {
            state.invalidateScroll()
            visiblePageSizes.clear()
        }
        loadedDocument = document
        scheduleWork()
    }

    fun onDocumentError() {
        loading = false
        loadedDocument = null
        visiblePageSizes.clear()
        state.cancel()
        onOperationStateChanged()
    }

    fun scrollToPage(pageIndex: Int) {
        state.request(pageIndex)
        onOperationStateChanged()
        scheduleWork()
    }

    fun unbind() {
        pdfView?.apply {
            removeOnViewportChangedListener(viewportListener)
            removeOnLayoutChangeListener(layoutListener)
            removeCallbacks(work)
        }
        pdfView = null
        loadedDocument = null
        viewStateRestored = false
        workPosted = false
        loading = true
        visiblePageSizes.clear()
        state.invalidateScroll()
    }

    private fun scheduleWork() {
        val view = pdfView ?: return
        if (workPosted) return
        workPosted = true
        view.post(work)
    }

    private fun applyPendingPage() {
        val view = pdfView ?: return
        val document = loadedDocument ?: return
        if (!viewStateRestored || view.pdfDocument !== document || !view.isLaidOut ||
            view.isLayoutRequested || view.width <= 0 || view.height <= 0
        ) return

        val wasLoading = loading
        loading = false
        if (document.pageCount <= 0) state.cancel()

        val target = state.takeScrollRequest(document.pageCount)
        if (target != null) {
            view.scrollToPage(target)
        }

        val pending = state.pendingPageIndex
        val bounds = pending?.let { pageIndex ->
            val size = visiblePageSizes[pageIndex] ?: return@let null
            val origin = view.pdfToViewPoint(PdfPoint(pageIndex, 0f, 0f)) ?: return@let null
            RectF(origin.x, origin.y, origin.x + size.width * view.zoom, origin.y + size.height * view.zoom)
        }
        val centerY = view.paddingTop + (view.height - view.paddingTop - view.paddingBottom) / 2f
        val completed = pending != null && bounds != null &&
            bounds.right > view.paddingLeft && bounds.left < view.width - view.paddingRight &&
            (centerY in bounds.top..bounds.bottom ||
                (bounds.top >= view.paddingTop && bounds.bottom <= view.height - view.paddingBottom)) &&
            state.confirmArrival(pending)
        if (wasLoading || completed) onOperationStateChanged()
    }

    private companion object {
        const val STATE_VISIBLE_PAGE = "pdf_navigation_visible_page"
        const val STATE_PENDING_PAGE = "pdf_navigation_pending_page"
    }
}