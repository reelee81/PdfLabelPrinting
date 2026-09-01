package hu.reelee81.pdflabelprinting

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.Toolbar.LayoutParams
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar

class PdfViewer : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "pdf_uri"
        const val EXTRA_OUTPUT_URI = "pdf_output_uri"
        private const val FRAG_TAG = "pdfFragTag"
    }

    private var currentUri: Uri? = null

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val editedUri = result.data
                ?.getStringExtra(PdfEditorActivity.EXTRA_OUTPUT_URI)
                ?.toUri()
                ?: return@registerForActivityResult

            currentUri = editedUri
            (supportFragmentManager.findFragmentByTag(FRAG_TAG) as? ReadOnlyPdfViewerFragment)
                ?.documentUri = editedUri

            findViewById<MaterialToolbar>(R.id.toolbar).title = resolveDisplayName(editedUri)
            setResult(
                RESULT_OK,
                Intent().putExtra(EXTRA_OUTPUT_URI, editedUri.toString())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.viewer_pdf)

        run {
            val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            val insets = WindowCompat.getInsetsController(window, window.decorView)
            insets.isAppearanceLightStatusBars = !night
            run {
                val navColor = ContextCompat.getColor(this, R.color.pdf_viewer_sb_bg_color)
                SystemBarsCompat.applyNavBarColor(this, navColor)
            }
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back_24)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val uriStr = intent.getStringExtra(EXTRA_URI)
        val uri: Uri? = uriStr?.toUri()
        if (uri == null) {
            Toast.makeText(this, getString(R.string.temporary_pdf_not_found), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        currentUri = uri

        toolbar.title = resolveDisplayName(uri)

        val supportsPdfViewer =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13

        if (!supportsPdfViewer) {
            Toast.makeText(
                this,
                getString(R.string.pdf_viewer_requires_s_ext_13),
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val frag = (supportFragmentManager.findFragmentByTag(FRAG_TAG) as? ReadOnlyPdfViewerFragment)
            ?: ReadOnlyPdfViewerFragment().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.pdfFragContainer, it, FRAG_TAG)
                    .commitNow()
            }

        frag.documentUri = uri

        addToolbarButtons(toolbar, frag)
    }

    private fun supportsPdfEditing(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 18

    private fun addToolbarButtons(toolbar: MaterialToolbar, frag: ReadOnlyPdfViewerFragment) {
        val buttonSize = resources.getDimensionPixelSize(R.dimen.dp_32)
        val oldSearchRightMargin = toolbar.contentInsetStartWithNavigation +
                resources.getDimensionPixelSize(R.dimen.dp_10)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                marginEnd = oldSearchRightMargin + resources.getDimensionPixelSize(R.dimen.dp_6)
            }
        }

        buttons.addView(
            createToolbarIconButton(R.drawable.ic_search_24, getString(R.string.search_pdf), buttonSize).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd =
                    resources.getDimensionPixelSize(R.dimen.dp_4)
                setOnClickListener {
                    frag.isTextSearchActive = true
                }
            }
        )

        if (supportsPdfEditing()) {
            buttons.addView(
                createToolbarIconButton(R.drawable.ic_edit_24, getString(R.string.edit_pdf), buttonSize).apply {
                    setOnClickListener {
                        val editUri = currentUri ?: return@setOnClickListener
                        editLauncher.launch(
                            Intent(this@PdfViewer, PdfEditorActivity::class.java).apply {
                                putExtra(PdfEditorActivity.EXTRA_INPUT_URI, editUri.toString())
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        )
                    }
                }
            )
        }

        toolbar.addView(buttons)
    }

    private fun createToolbarIconButton(
        iconRes: Int,
        description: String,
        size: Int
    ): AppCompatImageButton =
        AppCompatImageButton(this).apply {
            setImageDrawable(AppCompatResources.getDrawable(this@PdfViewer, iconRes))
            contentDescription = description
            imageTintList = AppCompatResources.getColorStateList(this@PdfViewer, R.color.toolbar_navigation_icon_color)
            background = null
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

    private fun resolveDisplayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIdx)
                } else {
                    uri.lastPathSegment
                }
            } ?: (uri.lastPathSegment ?: getString(R.string.app_name))
        } catch (_: Exception) {
            uri.lastPathSegment ?: getString(R.string.app_name)
        } ?: getString(R.string.app_name)
    }
}