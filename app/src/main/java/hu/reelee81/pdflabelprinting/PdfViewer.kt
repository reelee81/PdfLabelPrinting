package hu.reelee81.pdflabelprinting

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.Toolbar.LayoutParams
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class PdfViewer : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "pdf_uri"
        private const val FRAG_TAG = "pdfFragTag"
    }

    @SuppressLint("RtlHardcoded")
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

        val frag = (supportFragmentManager.findFragmentByTag(FRAG_TAG) as? PdfViewerFragment)
            ?: PdfViewerFragment().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.pdfFragContainer, it, FRAG_TAG)
                    .commitNow()
            }

        frag.documentUri = uri

        run {
            val ctx = ContextThemeWrapper(
                this,
                R.style.PdfLabelPrinting_TextButton
            )
            val btn = MaterialButton(ctx, null, 0).apply {
                icon = AppCompatResources.getDrawable(this@PdfViewer, R.drawable.ic_search_24)
                iconPadding = 0
                iconTint = AppCompatResources.getColorStateList(
                    this@PdfViewer,
                    R.color.toolbar_navigation_icon_color
                )

                backgroundTintList = AppCompatResources.getColorStateList(
                    this@PdfViewer,
                    android.R.color.transparent
                )

                rippleColor = AppCompatResources.getColorStateList(
                    this@PdfViewer,
                    android.R.color.transparent
                )

                text = ""

                val s = resources.getDimensionPixelSize(R.dimen.dp_44)
                layoutParams = LayoutParams(s, s).apply {
                    gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                    rightMargin = toolbar.contentInsetStartWithNavigation + resources.getDimensionPixelSize(R.dimen.dp_10)
                }

                setOnClickListener {
                    frag.isTextSearchActive = true
                }
            }
            toolbar.addView(btn)
        }
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