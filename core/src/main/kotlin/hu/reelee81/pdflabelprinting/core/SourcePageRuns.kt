package hu.reelee81.pdflabelprinting.core

import kotlin.math.min

object SourcePageRuns {
    fun removedPages(groups: Iterable<Int>, per: Int, total: Int): Set<Int> = buildSet {
        for (group in groups) {
            val start = group * per
            val end = min(total - 1, start + per - 1)
            for (i in start..end) add(i)
        }
    }

    fun kept(total: Int, removed: Set<Int>): List<IntRange> {
        val runs = ArrayList<IntRange>()
        var runStart = -1
        for (page in 0 until total) {
            if (page in removed) {
                if (runStart >= 0) runs += (runStart + 1)..page
                runStart = -1
            } else if (runStart < 0) {
                runStart = page
            }
        }
        if (runStart >= 0) runs += (runStart + 1)..total
        return runs
    }

    fun reordered(groupOrder: Iterable<Int>, per: Int, total: Int): List<IntRange> {
        val runs = ArrayList<IntRange>()
        var runStart = -1
        var runEnd = -1

        fun flush() {
            if (runStart < 0) return
            val first = runStart * per
            val last = min(total - 1, runEnd * per + per - 1)
            if (first <= last) runs += (first + 1)..(last + 1)
        }

        for (group in groupOrder) {
            if (runStart >= 0 && group == runEnd + 1) {
                runEnd = group
            } else {
                flush()
                runStart = group
                runEnd = group
            }
        }
        flush()
        return runs
    }
}
