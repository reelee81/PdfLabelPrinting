@file:OptIn(androidx.pdf.ExperimentalPdfApi::class)

package hu.reelee81.pdflabelprinting

import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.pdf.PdfDocument
import androidx.pdf.PdfWriteHandle
import androidx.pdf.ink.EditablePdfViewerFragment

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
class PdfEditorFragment : EditablePdfViewerFragment() {

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        (activity as? PdfEditorActivity)?.onEditorDocumentLoaded()
    }

    override fun onApplyEditsSuccess(handle: PdfWriteHandle) {
        (activity as? PdfEditorActivity)?.onApplyEditsSuccess(handle)
    }

    override fun onApplyEditsFailed(error: Throwable) {
        (activity as? PdfEditorActivity)?.onApplyEditsFailed(error)
    }
}