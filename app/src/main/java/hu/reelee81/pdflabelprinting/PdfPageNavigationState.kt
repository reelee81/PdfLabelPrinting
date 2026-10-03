package hu.reelee81.pdflabelprinting

internal class PdfPageNavigationState {
    var pendingPageIndex: Int? = null
        private set
    var visiblePageIndex = 0
        private set
    private var scrollIssued = false

    val currentPageIndex: Int
        get() = pendingPageIndex ?: visiblePageIndex

    fun request(pageIndex: Int) {
        pendingPageIndex = pageIndex.coerceAtLeast(0)
        scrollIssued = false
    }

    fun restore(visiblePageIndex: Int, pendingPageIndex: Int?) {
        this.visiblePageIndex = visiblePageIndex.coerceAtLeast(0)
        this.pendingPageIndex = pendingPageIndex?.coerceAtLeast(0)
        scrollIssued = false
    }

    fun observeVisiblePage(pageIndex: Int) {
        visiblePageIndex = pageIndex.coerceAtLeast(0)
    }

    fun invalidateScroll() {
        scrollIssued = false
    }

    fun takeScrollRequest(pageCount: Int): Int? {
        val pending = pendingPageIndex ?: return null
        if (scrollIssued || pageCount <= 0) return null
        val target = pending.coerceAtMost(pageCount - 1)
        pendingPageIndex = target
        scrollIssued = true
        return target
    }

    fun confirmArrival(pageIndex: Int): Boolean {
        if (!scrollIssued || pendingPageIndex != pageIndex) return false
        pendingPageIndex = null
        scrollIssued = false
        return true
    }

    fun cancel() {
        pendingPageIndex = null
        scrollIssued = false
    }
}