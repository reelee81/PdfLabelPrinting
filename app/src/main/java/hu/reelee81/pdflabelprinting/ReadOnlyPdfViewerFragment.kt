package hu.reelee81.pdflabelprinting

import androidx.pdf.PdfDocument
import androidx.pdf.viewer.fragment.PdfViewerFragment

class ReadOnlyPdfViewerFragment : PdfViewerFragment() {

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        isToolboxVisible = false
    }

    override fun onRequestImmersiveMode(enterImmersive: Boolean) {
        isToolboxVisible = false
    }

    override fun onResume() {
        super.onResume()
        isToolboxVisible = false
    }
}