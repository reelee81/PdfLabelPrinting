package hu.reelee81.pdflabelprinting.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SourcePageRunsTest {
    @Test
    fun theRemovedPagesOfGroupsStopAtTheLastPage() {
        assertPages(setOf(2, 3), SourcePageRuns.removedPages(listOf(1), per = 2, total = 5))
        assertPages(setOf(0, 1, 4), SourcePageRuns.removedPages(listOf(0, 2), per = 2, total = 5))
        assertPages(emptySet(), SourcePageRuns.removedPages(listOf(3), per = 2, total = 5))
    }

    @Test
    fun theKeptPagesAreCopiedInConsecutiveOneBasedRuns() {
        assertRuns(listOf(1..2, 5..5), SourcePageRuns.kept(5, setOf(2, 3)))
        assertRuns(listOf(2..2, 4..5), SourcePageRuns.kept(5, setOf(0, 2)))
        assertRuns(listOf(1..5), SourcePageRuns.kept(5, emptySet()))
        assertRuns(emptyList(), SourcePageRuns.kept(2, setOf(0, 1)))
    }

    @Test
    fun aReorderJoinsFollowingGroupsIntoOneRun() {
        assertRuns(listOf(5..5, 1..4), SourcePageRuns.reordered(listOf(2, 0, 1), per = 2, total = 5))
        assertRuns(listOf(3..4, 1..2, 5..5), SourcePageRuns.reordered(listOf(1, 0, 2), per = 2, total = 5))
        assertRuns(listOf(1..5), SourcePageRuns.reordered(listOf(0, 1, 2), per = 2, total = 5))
    }

    @Test
    fun aReorderSkipsGroupsBeyondTheLastPage() {
        assertRuns(listOf(1..3), SourcePageRuns.reordered(listOf(0, 1, 2), per = 3, total = 3))
        assertRuns(listOf(1..1), SourcePageRuns.reordered(listOf(4, 0), per = 1, total = 1))
    }

    private fun assertPages(expected: Set<Int>, actual: Set<Int>) {
        assertEquals(expected, actual)
    }

    private fun assertRuns(expected: List<IntRange>, actual: List<IntRange>) {
        assertEquals(expected, actual)
    }
}
