package hu.reelee81.pdflabelprinting.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkFileBackupTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun backupIn(dir: File) = WorkFileBackup(dir, "source.pdf", "plp.pdf", "source_copy.pdf", "plp_copy.pdf")

    @Test
    fun aBackupCopiesBothWorkFilesAndReplacesOlderCopies() {
        val dir = temp.newFolder()
        File(dir, "source.pdf").writeText("source")
        File(dir, "plp.pdf").writeText("plp")
        File(dir, "source_copy.pdf").writeText("an older and longer source copy")

        backupIn(dir).backup()

        assertEquals("source", File(dir, "source_copy.pdf").readText())
        assertEquals("plp", File(dir, "plp_copy.pdf").readText())
    }

    @Test
    fun aMissingWorkFileGetsNoCopy() {
        val dir = temp.newFolder()
        File(dir, "source.pdf").writeText("source")

        backupIn(dir).backup()

        assertTrue(File(dir, "source_copy.pdf").exists())
        assertFalse(File(dir, "plp_copy.pdf").exists())
    }

    @Test
    fun aRestoreBringsBackBothFilesAndClearsEverythingElse() {
        val dir = temp.newFolder()
        File(dir, "source.pdf").writeText("source")
        File(dir, "plp.pdf").writeText("plp")
        val backup = backupIn(dir)
        backup.backup()
        File(dir, "source.pdf").writeText("half written")
        File(dir, "plp.pdf").delete()
        File(dir, "source_temp_work.pdf").writeText("work")

        assertTrue(backup.restore())

        assertEquals(setOf("source.pdf", "plp.pdf"), dir.list()!!.toSet())
        assertEquals("source", File(dir, "source.pdf").readText())
        assertEquals("plp", File(dir, "plp.pdf").readText())
    }

    @Test
    fun aRestoreWithoutALayoutCopyReportsItAndKeepsOnlyTheSource() {
        val dir = temp.newFolder()
        File(dir, "source.pdf").writeText("source")
        val backup = backupIn(dir)
        backup.backup()
        File(dir, "plp.pdf").writeText("new layout")

        assertFalse(backup.restore())

        assertEquals(setOf("source.pdf"), dir.list()!!.toSet())
        assertEquals("source", File(dir, "source.pdf").readText())
    }

    @Test
    fun aSourceFolderIsBackedUpAndRestoredWithAllItsParts() {
        val dir = temp.newFolder()
        val source = File(dir, "source").apply { mkdirs() }
        File(source, "0001.pdf").writeText("first")
        File(source, "0002.pdf").writeText("second")
        File(dir, "plp.pdf").writeText("plp")
        File(dir, "source_copy").apply { mkdirs() }.resolve("0009.pdf").writeText("an older copy")
        val backup = WorkFileBackup(dir, "source", "plp.pdf", "source_copy", "plp_copy.pdf")

        backup.backup()
        assertEquals(setOf("0001.pdf", "0002.pdf"), File(dir, "source_copy").list()!!.toSet())

        File(source, "0002.pdf").writeText("half written")
        File(source, "0003.pdf").writeText("new part")
        File(source, "0003.pdf.work").writeText("work")
        File(dir, "other").apply { mkdirs() }.resolve("kept.tmp").writeText("other")

        assertTrue(backup.restore())

        assertEquals(setOf("source", "plp.pdf", "other"), dir.list()!!.toSet())
        assertEquals(setOf("0001.pdf", "0002.pdf"), source.list()!!.toSet())
        assertEquals("first", File(source, "0001.pdf").readText())
        assertEquals("second", File(source, "0002.pdf").readText())
        assertEquals("plp", File(dir, "plp.pdf").readText())
    }

    @Test
    fun aRestoreWithoutCopiesEmptiesTheFolder() {
        val dir = temp.newFolder()
        File(dir, "source.pdf").writeText("source")
        File(dir, "other.tmp").writeText("other")

        assertFalse(backupIn(dir).restore())

        assertEquals(emptySet<String>(), dir.list()!!.toSet())
    }
}
