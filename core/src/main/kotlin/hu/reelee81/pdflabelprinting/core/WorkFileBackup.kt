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
            runCatching {
                FileInputStream(source).use { input ->
                    FileOutputStream(sourceCopy, false).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }

        if (plp.exists()) {
            runCatching {
                FileInputStream(plp).use { input ->
                    FileOutputStream(plpCopy, false).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    fun restore(): Boolean {
        var restoredPlpBackup = false

        dir.listFiles()?.forEach { f ->
            if (f != sourceCopy && f != plpCopy) {
                runCatching { f.delete() }
            }
        }

        if (sourceCopy.exists()) {
            runCatching {
                FileInputStream(sourceCopy).use { input ->
                    FileOutputStream(source, false).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            runCatching { sourceCopy.delete() }
        }

        if (plpCopy.exists()) {
            runCatching {
                FileInputStream(plpCopy).use { input ->
                    FileOutputStream(plp, false).use { output ->
                        input.copyTo(output)
                    }
                }
            }.onSuccess {
                restoredPlpBackup = true
            }

            runCatching { plpCopy.delete() }
        }

        return restoredPlpBackup
    }
}
