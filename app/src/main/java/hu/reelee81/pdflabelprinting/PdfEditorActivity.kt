@file:OptIn(androidx.pdf.ExperimentalPdfApi::class)

package hu.reelee81.pdflabelprinting

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresExtension
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar.LayoutParams
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.pdf.PdfWriteHandle
import com.itextpdf.kernel.pdf.PdfDocument as ITextPdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.utils.PdfAnnotationFlattener
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INPUT_URI = "pdf_input_uri"
        const val EXTRA_OUTPUT_URI = "pdf_output_uri"
        private const val FRAG_TAG = "pdfEditorFrag"
    }

    private lateinit var fragment: PdfEditorFragment
    private var saveButton: MaterialButton? = null
    private var saving = false
    private var documentLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!supportsPdfEditing()) {
            Toast.makeText(this, getString(R.string.pdf_editor_requires_s_ext_18), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.viewer_pdf)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !night
        SystemBarsCompat.applyNavBarColor(this, ContextCompat.getColor(this, R.color.pdf_viewer_sb_bg_color))

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.title = getString(R.string.edit_pdf)
        toolbar.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back_24)
        toolbar.setNavigationOnClickListener { finishOrConfirmDiscard() }

        val uri = intent.getStringExtra(EXTRA_INPUT_URI)?.toUri()
        if (uri == null) {
            Toast.makeText(this, getString(R.string.temporary_pdf_not_found), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        fragment = (supportFragmentManager.findFragmentByTag(FRAG_TAG) as? PdfEditorFragment)
            ?: PdfEditorFragment().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.pdfFragContainer, it, FRAG_TAG)
                    .commitNow()
            }

        fragment.documentUri = uri

        addSaveButton(toolbar)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishOrConfirmDiscard()
            }
        })
    }

    private fun addSaveButton(toolbar: MaterialToolbar) {
        val s = resources.getDimensionPixelSize(R.dimen.dp_44)
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.dp_16)

        saveButton = MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonStyle
        ).apply {
            text = getString(R.string.save_pdf_edits)
            minWidth = 0
            minimumWidth = 0
            insetLeft = 0
            insetRight = 0
            setPaddingRelative(horizontalPadding, paddingTop, horizontalPadding, paddingBottom)
            isEnabled = false
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, s).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                rightMargin = resources.getDimensionPixelSize(R.dimen.dp_20)
            }
            setOnClickListener { saveEdits() }
        }

        toolbar.addView(saveButton)
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
    fun onEditorDocumentLoaded() {
        documentLoaded = true
        saveButton?.isEnabled = true
        fragment.view?.post {
            if (!saving && !isFinishing && !isDestroyed) {
                fragment.isEditModeEnabled = true
            }
        }
    }

    private fun saveEdits() {
        if (!supportsPdfEditing()) return
        if (!documentLoaded) return
        if (saving || fragment.isApplyEditsInProgress) return

        if (!fragment.hasUnsavedChanges) {
            Toast.makeText(this, getString(R.string.no_pdf_edits_to_save), Toast.LENGTH_SHORT).show()
            return
        }

        saving = true
        saveButton?.isEnabled = false
        fragment.applyDraftEdits()
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
    fun onApplyEditsSuccess(handle: PdfWriteHandle) {
        lifecycleScope.launch {
            try {
                val outFile = File(cacheDir, "edited_${System.currentTimeMillis()}.pdf")

                withContext(Dispatchers.IO) {
                    handle.use { writeHandle ->
                        ParcelFileDescriptor.open(
                            outFile,
                            ParcelFileDescriptor.MODE_CREATE or
                                    ParcelFileDescriptor.MODE_TRUNCATE or
                                    ParcelFileDescriptor.MODE_READ_WRITE
                        ).use { pfd ->
                            writeHandle.writeTo(pfd)
                        }
                    }
                    flattenVisibleAnnotations(outFile)
                }

                val outUri = FileProvider.getUriForFile(this@PdfEditorActivity, "$packageName.provider", outFile)
                setResult(
                    RESULT_OK,
                    Intent().putExtra(EXTRA_OUTPUT_URI, outUri.toString())
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
                fragment.isEditModeEnabled = false
                finish()
            } catch (e: Exception) {
                saving = false
                saveButton?.isEnabled = true
                Toast.makeText(this@PdfEditorActivity, getString(R.string.pdf_edits_save_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun onApplyEditsFailed(error: Throwable) {
        saving = false
        saveButton?.isEnabled = true
        Toast.makeText(this, getString(R.string.pdf_edits_save_failed, error.message), Toast.LENGTH_LONG).show()
    }

    private fun flattenVisibleAnnotations(file: File) {
        val flatFile = File(cacheDir, "flattened_${System.currentTimeMillis()}.pdf")
        if (flatFile.exists()) flatFile.delete()

        ITextPdfDocument(PdfReader(file.absolutePath), PdfWriter(flatFile.absolutePath)).use { pdf ->
            val flattener = PdfAnnotationFlattener()
            for (pageNumber in 1..pdf.numberOfPages) {
                val page = pdf.getPage(pageNumber)
                val annotations = page.annotations
                    .filter { annotation ->
                        when (annotation.subtype) {
                            PdfName.Stamp,
                            PdfName.Ink,
                            PdfName.Highlight,
                            PdfName.FreeText -> true
                            else -> false
                        }
                    }
                    .toList()

                if (annotations.isNotEmpty()) {
                    flattener.flatten(annotations)
                }
            }
        }

        if (file.exists()) file.delete()
        if (!flatFile.renameTo(file)) {
            flatFile.copyTo(file, overwrite = true)
            flatFile.delete()
        }
    }

    private fun finishOrConfirmDiscard() {
        val overlay = findViewById<View>(R.id.overlay_dialog_pdf_editor_discard)
        if (overlay.isVisible) {
            hideDiscardConfirmation()
            return
        }

        if (!supportsPdfEditing()) {
            finish()
            return
        }

        if (::fragment.isInitialized && fragment.hasUnsavedChanges) {
            showDiscardConfirmation()
        } else {
            finish()
        }
    }

    private fun showDiscardConfirmation() {
        val overlay = findViewById<View>(R.id.overlay_dialog_pdf_editor_discard)
        val panel = findViewById<View>(R.id.overlay_dialog_pdf_editor_discard_panel)
        val cancel = findViewById<MaterialButton>(R.id.pdf_editor_discard_cancel)
        val discard = findViewById<MaterialButton>(R.id.pdf_editor_discard_confirm)

        overlay.setOnClickListener { hideDiscardConfirmation() }
        panel.setOnClickListener { }
        cancel.setOnClickListener { hideDiscardConfirmation() }
        discard.setOnClickListener {
            hideDiscardConfirmation()
            finish()
        }

        overlay.isVisible = true
    }

    private fun hideDiscardConfirmation() {
        findViewById<View>(R.id.overlay_dialog_pdf_editor_discard).apply {
            isVisible = false
            setOnClickListener(null)
        }
    }

    @ChecksSdkIntAtLeast(extension = Build.VERSION_CODES.S, api = 18)
    private fun supportsPdfEditing(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 18
}