package hu.reelee81.pdflabelprinting.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WorkFileBackup(
    private val dir: File,
    sourceName: String,
    plpName: String,
    sourceCopyName: String,
    plpCopyName: String
) {
    private val source = File(dir, sourceName)
    private val plp = File(dir, plpName)
    private val sourceCopy = File(dir, sourceCopyName)
    private val plpCopy = File(dir, plpCopyName)

    fun backup() {
        if (source.exists()) {
            runCatching { copy(source, sourceCopy) }
        }

        if (plp.exists()) {
            runCatching { copy(plp, plpCopy) }
        }
    }

    fun restore(): Boolean {
        var restoredPlpBackup = false

        dir.listFiles()?.forEach { f ->
            if (f != sourceCopy && f != plpCopy) {
                runCatching { if (f == source || f == plp) f.deleteRecursively() else f.delete() }
            }
        }

        if (sourceCopy.exists()) {
            runCatching { copy(sourceCopy, source) }

            runCatching { sourceCopy.deleteRecursively() }
        }

        if (plpCopy.exists()) {
            runCatching { copy(plpCopy, plp) }.onSuccess {
                restoredPlpBackup = true
            }

            runCatching { plpCopy.deleteRecursively() }
        }

        return restoredPlpBackup
    }

    private fun copy(from: File, to: File) {
        if (from.isDirectory) {
            to.deleteRecursively()
            check(from.copyRecursively(to)) { "Could not copy ${from.name}" }
            return
        }
        FileInputStream(from).use { input ->
            FileOutputStream(to, false).use { output ->
                input.copyTo(output)
            }
        }
    }
}
