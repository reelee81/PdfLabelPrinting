package hu.reelee81.pdflabelprinting

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.text.style.AbsoluteSizeSpan
import android.util.AttributeSet
import android.util.Log
import android.util.Xml
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.view.MenuCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.exceptions.BadPasswordException
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.CompressionConstants
import com.itextpdf.kernel.pdf.EncryptionConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfVersion
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.ReaderProperties
import com.itextpdf.kernel.pdf.WriterProperties
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import org.xmlpull.v1.XmlPullParser
import hu.reelee81.pdflabelprinting.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import kotlin.coroutines.resume
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

class MainActivity : AppCompatActivity() {

    private companion object {
        const val PREFS_NAME_FRAGMENT = "PdfLabelPrintingPrefs"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ROWS = "rows"
        const val KEY_NORMAL_ROWS = "normal_rows"
        const val KEY_LABEL_ROWS = "label_rows"
        const val KEY_COLUMNS = "columns"
        const val KEY_NORMAL_COLUMNS = "normal_columns"
        const val KEY_LABEL_COLUMNS = "label_columns"
        const val KEY_MARGIN = "margin"
        const val KEY_NORMAL_MARGIN = "normal_margin"
        const val KEY_LABEL_MARGIN = "label_margin"
        const val KEY_ORIENTATION = "orientation"
        const val KEY_NORMAL_ORIENTATION = "normal_orientation"
        const val KEY_LABEL_ORIENTATION = "label_orientation"
        const val KEY_DRAW_FRAME = "draw_frame"
        const val KEY_NORMAL_DRAW_FRAME = "normal_draw_frame"
        const val KEY_LABEL_DRAW_FRAME = "label_draw_frame"
        const val KEY_SIG_POS_X = "sig_pos_x"
        const val KEY_NORMAL_SIG_POS_X = "normal_sig_pos_x"
        const val KEY_LABEL_SIG_POS_X = "label_sig_pos_x"
        const val KEY_SIG_POS_Y = "sig_pos_y"
        const val KEY_NORMAL_SIG_POS_Y = "normal_sig_pos_y"
        const val KEY_LABEL_SIG_POS_Y = "label_sig_pos_y"
        const val KEY_SCALE = "scale"
        const val KEY_NORMAL_SCALE = "normal_scale"
        const val KEY_LABEL_SCALE = "label_scale"
        const val KEY_BATCH_PAGE = "batch_page"
        const val KEY_NORMAL_BATCH_PAGE = "normal_batch_page"
        const val KEY_LABEL_BATCH_PAGE = "label_batch_page"
        const val KEY_PASSWORD_OUT = "password_out"
        const val KEY_NORMAL_PASSWORD_OUT = "normal_password_out"
        const val KEY_LABEL_PASSWORD_OUT = "label_password_out"
        const val KEY_FILE_NAME_TEMPLATE = "file_name_template"
        const val KEY_NORMAL_FILE_NAME_TEMPLATE = "normal_file_name_template"
        const val KEY_LABEL_FILE_NAME_TEMPLATE = "label_file_name_template"
        const val KEY_LAST_SIG_DPI_INDEX = "key_last_signature_dpi_index"
        const val KEY_LAST_SCAN_EFFECT = "key_last_scan_effect"
        const val KEY_LAST_GRAYSCALE = "key_last_grayscale_page"
        const val KEY_LAST_INNER_PDF_READER = "key_last_inner_pdf_reader"
        const val KEY_LAST_LOADED_PROFILE = "key_last_loaded_profile"
        const val KEY_RESTORE_LOCK_UNTIL_RELOAD = "key_restore_lock_until_reload"
        const val KEY_FIRST_RUN_INITIALIZED = "key_first_run_initialized"
        const val KEY_THEME_RESTORE_FIRST = "key_theme_restore_first"
        const val KEY_THEME_RESTORE_OFFSET = "key_theme_restore_offset"
        const val KEY_THEME_RESTORE_SELECTED_CSV = "key_theme_restore_selected_csv"
        const val KEY_THEME_RESTORE_NSV_Y = "key_theme_restore_nsv_y"
        const val DEFAULT_SIG_POS_X_MM = 155
        const val DEFAULT_SIG_POS_Y_MM = 245

        private const val SOURCE_TEMP_NAME = "source_temp.pdf"
        private const val SOURCE_TEMP_WORK = "source_temp_work.pdf"
        private const val PLP_TEMP_NAME = "plp_temp.pdf"
        private const val MAX_BASE_LEN = 251
        private const val MAX_INPUT = 100
        private const val KEEP_RECENT_EXPORTS = 0
        private const val MAX_IMG_W = 8411
        private const val MAX_IMG_H = 8411
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: PageItemsViewModel
    private lateinit var adapter: ThumbnailAdapter
    private lateinit var timeStamp: String
    private lateinit var pdfPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var progressDialog: AlertDialog
    private lateinit var signaturesDir: File
    private lateinit var signaturePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var numberOfRows: TextInputEditText
    private lateinit var numberOfColumns: TextInputEditText
    private lateinit var margin: TextInputEditText
    private lateinit var signaturePosX: TextInputEditText
    private lateinit var signaturePosY: TextInputEditText
    private lateinit var emptyPage: MaterialButton
    private lateinit var printPdf: MaterialButton
    private lateinit var savePdf: MaterialButton
    private lateinit var sendPdf: MaterialButton
    private lateinit var addSignature: MaterialButton
    private lateinit var portrait: MaterialRadioButton
    private lateinit var landscape: MaterialRadioButton
    private lateinit var drawFrameSwitch: SwitchMaterial
    private lateinit var scaleSwitch: SwitchMaterial
    private lateinit var listStartButton: AppCompatImageButton
    private lateinit var listEndButton: AppCompatImageButton
    private lateinit var saveDocumentLauncher: ActivityResultLauncher<Intent>
    private lateinit var batchPage: TextInputEditText
    private lateinit var passwordOutCb: MaterialCheckBox

    private var pendingSaveNamedPath: String? = null
    private var pendingSaveNamedDisplayName: String? = null
    private var isLoadingSettingsProfile = false
    private var regenRequestedDuringProfileLoad = false
    private var nbColumnsValid = true
    private var nbRowsValid = true
    private var marginValid = true
    private var sigXValid = true
    private var sigYValid = true
    private var currentSigFile: File? = null
    private var selectedSigIndex = -1
    private var fastScroller: AlwaysVisibleFastScroller? = null
    private var orderDirty = false
    private var lastGlobalAllSelected: Boolean? = null
    private var lastGrantedViewUri: Uri? = null
    private var lastGrantedPkg: String? = null
    private var orientationLockPrevRequested: Int? = null
    private var selectedDpiIndexForSignature = 0
    private var didRunNScrollColdStartFix = false
    private var isFirstCreate = true
    private var batchValid = true

    @Volatile private var isPlpRebuilding = false
    @Volatile private var clearSelectionOnNextRebuild = false

    private var suppressNup: Boolean = true

    private data class NupConfig(
        val rows: Int,
        val cols: Int,
        val marginMm: Int,
        val isLandscape: Boolean,
        val drawFrame: Boolean,
        val scaleIndividually: Boolean
    )
    private var lastBuiltNupConfig: NupConfig? = null

    private val savePrefsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            val uri = result.data!!.data!!
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val xml = exportAllSharedPreferencesXml()
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(xml.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: throw IOException(getString(R.string.output_stream_is_null))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.msg_prefs_saved_success), Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.msg_prefs_save_failed, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private val loadPrefsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            val uri = result.data!!.data!!
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val xml = contentResolver.openInputStream(uri)?.use { inp ->
                        inp.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IOException(getString(R.string.input_stream_is_null))

                    importAllSharedPreferencesFromXml(xml)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.msg_prefs_loaded_success), Toast.LENGTH_LONG).show()

                        val sp = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
                        val profile = sp.getString(KEY_LAST_LOADED_PROFILE, "") ?: ""

                        when (profile) {
                            "normal" -> {
                                findViewById<Button>(R.id.load_normal_pages)?.performClick()
                            }
                            "label" -> {
                                findViewById<Button>(R.id.load_label_pages)?.performClick()
                            }
                            else -> {

                            }
                        }

                        invalidateOptionsMenu()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.msg_prefs_load_failed, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        if (savedInstanceState == null) {
            clearCacheDir()
        }

        val prefs = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
        val theme = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
        val mode = when (theme) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        super.onCreate(savedInstanceState)

        isFirstCreate = (savedInstanceState == null)

        // START Alternatív DEBUG mód ellenőrzés
        val isDebug = try {
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Exception) {
            false
        }

        if (isDebug) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }
        // STOP Alternatív DEBUG mód ellenőrzés

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        run {
            val navColor = ContextCompat.getColor(this, R.color.app_background_color)
            SystemBarsCompat.applyNavBarColor(this, navColor)
        }

        setSupportActionBar(binding.toolbar)

        setThemedBackArrow()
        binding.toolbar.setNavigationOnClickListener { finishAndRemoveTask() }

        signaturesDir = File(filesDir, "Signatures")
        if (!signaturesDir.exists()) {
            signaturesDir.mkdirs()
        }

        val rvThumbnails: RecyclerView = findViewById(R.id.rv_thumbnails)
        numberOfRows = findViewById(R.id.number_of_rows)
        numberOfColumns = findViewById(R.id.number_of_columns)
        margin = findViewById(R.id.margin)
        batchPage = findViewById(R.id.batch_page)
        passwordOutCb = findViewById(R.id.password_out)
        emptyPage = findViewById(R.id.empty_page)
        val selectPdf: Button = findViewById(R.id.select_pdf)
        printPdf = findViewById(R.id.print_pdf)
        savePdf = findViewById(R.id.save_pdf_file)
        sendPdf = findViewById(R.id.send_pdf)
        addSignature = findViewById(R.id.add_signature)
        portrait = findViewById(R.id.portrait)
        landscape = findViewById(R.id.landscape)
        drawFrameSwitch = findViewById(R.id.drawframe)

        listStartButton = findViewById(R.id.list_start)
        listEndButton = findViewById(R.id.list_end)

        listStartButton.setOnClickListener {
            binding.rvThumbnails.scrollToPosition(0)
        }

        listEndButton.setOnClickListener {
            if (viewModel.pageItems.isNotEmpty()) {
                binding.rvThumbnails.scrollToPosition(viewModel.pageItems.size - 1)
            }
        }

        scaleSwitch = findViewById(R.id.scale)

        signaturePosX = findViewById(R.id.signature_position_x)
        signaturePosY = findViewById(R.id.signature_position_y)

        val addTimeBtn: ImageButton = findViewById(R.id.add_time)

        numberOfRows.filters    = arrayOf(InputFilter.LengthFilter(3), numberRangeFilterNoOp(1..100))
        numberOfColumns.filters = arrayOf(InputFilter.LengthFilter(3), numberRangeFilterNoOp(1..100))
        margin.filters          = arrayOf(InputFilter.LengthFilter(3), numberRangeFilterNoOp(0..100))
        batchPage.filters       = arrayOf(InputFilter.LengthFilter(3), numberRangeFilterNoOp(1..100))

        portrait.setOnCheckedChangeListener { _, checked ->
            updateSignatureInputFilters()
            if (checked) maybeRegeneratePlpOnSettingChange()
        }
        landscape.setOnCheckedChangeListener { _, checked ->
            updateSignatureInputFilters()
            if (checked) maybeRegeneratePlpOnSettingChange()
        }

        drawFrameSwitch.setOnCheckedChangeListener { _, _ -> maybeRegeneratePlpOnSettingChange() }
        scaleSwitch.setOnCheckedChangeListener { _, _ -> maybeRegeneratePlpOnSettingChange() }

        setupRegenerateOnBlur(numberOfRows)
        setupRegenerateOnBlur(numberOfColumns)
        setupRegenerateOnBlur(margin)

        progressDialog = MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.in_progress_my))
            .setCancelable(false)
            .create()

        viewModel = ViewModelProvider(this)[PageItemsViewModel::class.java]
        val pageItems = viewModel.pageItems

        loadSettings()

        timeStamp = System.currentTimeMillis().toString()

        pdfPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                result.data?.data?.let { handlePdfUri(it) }
            }
        }

        signaturePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val uri = result.data?.data
                uri?.let { copySignatureToAppDir(it) }
            }
        }

        saveDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val uri = result.data?.data
                if (uri != null) {
                    showInProgress(getString(R.string.in_progress_my))
                    lockScreenOrientation()
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val srcPath = pendingSaveNamedPath
                            if (srcPath.isNullOrEmpty()) throw IllegalStateException(getString(R.string.temporary_pdf_not_found))
                            val srcFile = File(srcPath)
                            if (!srcFile.exists()) throw IllegalStateException(getString(R.string.temporary_pdf_not_found))

                            contentResolver.openOutputStream(uri)?.use { out ->
                                FileInputStream(srcFile).use { inp ->
                                    val buffer = ByteArray(16 * 1024)
                                    var read: Int
                                    while (inp.read(buffer).also { read = it } > 0) {
                                        out.write(buffer, 0, read)
                                    }
                                    out.flush()
                                }
                            } ?: throw IOException(getString(R.string.input_stream_is_null))

                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.pdf_saved_successfully),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.failed_to_generate_pdf_with_msg, e.message),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                hideInProgressIfShown()
                                unlockScreenOrientation()
                                pendingSaveNamedPath = null
                                pendingSaveNamedDisplayName = null
                            }
                        }
                    }
                } else {
                    pendingSaveNamedPath = null
                    pendingSaveNamedDisplayName = null
                }
            } else {
                pendingSaveNamedPath = null
                pendingSaveNamedDisplayName = null
            }
        }

        rvThumbnails.layoutManager = GridLayoutManager(this, 2)
        adapter = ThumbnailAdapter(
            pageItems,
            deleteListener = { pos -> deletePlpPageGroupAndMirrorSource(pos) },
            clickListener = { _ -> openPdfWithDriveOrOther() },
            selectionChanged = { _, _ -> updateButtonsState() }
        )
        rvThumbnails.adapter = adapter

        rvThumbnails.post {
            val lm = rvThumbnails.layoutManager as? GridLayoutManager ?: return@post

            val first = lm.findFirstVisibleItemPosition().takeIf { it != RecyclerView.NO_POSITION } ?: 0
            val last  = lm.findLastVisibleItemPosition().takeIf  { it != RecyclerView.NO_POSITION } ?: 0

            val start = (first - 4).coerceAtLeast(0)
            val end   = (last + 4).coerceAtMost(adapter.itemCount - 1)
            val r = start..end

            adapter.prefetchAroundVisible(this@MainActivity, r)
        }

        rvThumbnails.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as? GridLayoutManager ?: return
                val first = lm.findFirstVisibleItemPosition()
                val last = lm.findLastVisibleItemPosition()
                if (first != RecyclerView.NO_POSITION && last != RecyclerView.NO_POSITION) {
                    val start = (first - 4).coerceAtLeast(0)
                    val end   = (last + 4).coerceAtMost(adapter.itemCount - 1)
                    adapter.prefetchAroundVisible(this@MainActivity, start..end)
                }
            }
        })

        binding.rvThumbnails.setHasFixedSize(true)
        binding.rvThumbnails.itemAnimator = null

        fastScroller = AlwaysVisibleFastScroller(binding.rvThumbnails, this)
        fastScroller?.setEnabledWhen(viewModel.pageItems.isNotEmpty())

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            private var userDragActive = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                val moved = pageItems.removeAt(from)
                pageItems.add(to, moved)
                adapter.notifyItemMoved(from, to)
                orderDirty = true
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                userDragActive = (actionState == ItemTouchHelper.ACTION_STATE_DRAG)

                if (userDragActive) {
                    try { fastScroller?.setTemporarilyDisabled(true) } catch (_: Exception) {}
                }

                try {
                    (findViewById<RecyclerView>(R.id.rv_thumbnails).parent as? ViewGroup)
                        ?.requestDisallowInterceptTouchEvent(userDragActive)
                } catch (_: Exception) {}
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                try { recyclerView.parent?.requestDisallowInterceptTouchEvent(false) } catch (_: Exception) {}

                if (orderDirty) {
                    val doPersist = {
                        if (orderDirty) {
                            persistPlpOrderToSourceTemp()
                            orderDirty = false
                        }
                        try { fastScroller?.setTemporarilyDisabled(false) } catch (_: Exception) {}
                        updateButtonsState()
                    }

                    val settleCheck = object : Runnable {
                        override fun run() {
                            if (userDragActive ||
                                recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE ||
                                recyclerView.isComputingLayout
                            ) {
                                recyclerView.postDelayed(this, 16L)
                            } else {
                                doPersist()
                            }
                        }
                    }
                    recyclerView.post(settleCheck)

                } else {
                    try { fastScroller?.setTemporarilyDisabled(false) } catch (_: Exception) {}
                    updateButtonsState()
                }
            }
        }).attachToRecyclerView(rvThumbnails)

        numberOfColumns.addTextChangedListener(SimpleTextWatcher { validateInputs() })
        numberOfRows.addTextChangedListener(SimpleTextWatcher { validateInputs() })
        margin.addTextChangedListener(SimpleTextWatcher { validateInputs() })
        signaturePosX.addTextChangedListener(SimpleTextWatcher { validateInputs() })
        signaturePosY.addTextChangedListener(SimpleTextWatcher { validateInputs() })
        batchPage.addTextChangedListener(SimpleTextWatcher { validateInputs() })

        emptyPage.setOnClickListener { addEmptyPageToSource() }
        selectPdf.setOnClickListener { launchSelectPdf() }
        printPdf.setOnClickListener { printNupPdf() }
        savePdf.setOnClickListener { saveNupPdf() }
        sendPdf.setOnClickListener { shareNupPdf() }
        addSignature.setOnClickListener { showSignatureDialog() }

        addTimeBtn.setOnClickListener { showTimestampDialog() }

        val saveNormalPages: Button = findViewById(R.id.save_normal_pages)
        val saveLabelPages: Button = findViewById(R.id.save_label_pages)
        saveNormalPages.setOnClickListener {
            saveSettings("normal")
            numberOfRows.clearFocus()
            numberOfColumns.clearFocus()
            margin.clearFocus()
            signaturePosX.clearFocus()
            signaturePosY.clearFocus()
            maybeRegeneratePlpOnSettingChange()
        }
        saveLabelPages.setOnClickListener {
            saveSettings("label")
            numberOfRows.clearFocus()
            numberOfColumns.clearFocus()
            margin.clearFocus()
            signaturePosX.clearFocus()
            signaturePosY.clearFocus()
            maybeRegeneratePlpOnSettingChange()
        }

        val normalPages: Button = findViewById(R.id.load_normal_pages)
        val labelPages: Button = findViewById(R.id.load_label_pages)
        normalPages.setOnClickListener {
            loadSettings("normal")
            maybeRegeneratePlpOnSettingChange()
        }
        labelPages.setOnClickListener {
            loadSettings("label")
            maybeRegeneratePlpOnSettingChange()
        }

        val intent = intent

        handleIncomingShareIntent(intent)
        setIntent(Intent())

        runCatching {
            val sp = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
            val restFirst = sp.getInt(KEY_THEME_RESTORE_FIRST, RecyclerView.NO_POSITION)
            val restOffset = sp.getInt(KEY_THEME_RESTORE_OFFSET, 0)
            val csv = sp.getString(KEY_THEME_RESTORE_SELECTED_CSV, "") ?: ""
            val nsvY = sp.getInt(KEY_THEME_RESTORE_NSV_Y, -1)
            val indicesToSelect: Set<Int>? = csv.split(',')
                .mapNotNull { it.toIntOrNull() }
                .toSet()
                .takeIf { it.isNotEmpty() }

            if (restFirst != RecyclerView.NO_POSITION || indicesToSelect != null) {
                sp.edit {
                    remove(KEY_THEME_RESTORE_FIRST)
                    remove(KEY_THEME_RESTORE_OFFSET)
                    remove(KEY_THEME_RESTORE_SELECTED_CSV)
                    remove(KEY_THEME_RESTORE_NSV_Y)
                }

                val spLock = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
                val needLock = spLock.getBoolean(KEY_RESTORE_LOCK_UNTIL_RELOAD, false)
                if (needLock) {
                    showInProgress(getString(R.string.in_progress_my))
                    lockScreenOrientation()
                }

                reloadThumbnailsFromPlp(
                    targetIndex = restFirst,
                    targetOffsetPx = restOffset,
                    restorePrevious = false,
                    indicesToSelect = indicesToSelect,
                    releaseUiAtStart = false,
                    onStarted = {
                        hideInProgressIfShown()
                        unlockScreenOrientation()
                        spLock.edit { putBoolean(KEY_RESTORE_LOCK_UNTIL_RELOAD, false) }
                    }
                )

                if (nsvY >= 0) {
                    val nsv = findViewById<NestedScrollView>(R.id.root_scroll)
                    nsv?.post { nsv.scrollTo(0, nsvY) }
                }
            } else {
                val spLock = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
                val needLock = spLock.getBoolean(KEY_RESTORE_LOCK_UNTIL_RELOAD, false)
                if (needLock) {
                    showInProgress(getString(R.string.in_progress_my))
                    lockScreenOrientation()
                }

                reloadThumbnailsFromPlp(
                    onStarted = {
                        hideInProgressIfShown()
                        unlockScreenOrientation()
                        spLock.edit { putBoolean(KEY_RESTORE_LOCK_UNTIL_RELOAD, false) }
                    }
                )
            }
        }.onFailure {
            val spLock = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
            val needLock = spLock.getBoolean(KEY_RESTORE_LOCK_UNTIL_RELOAD, false)
            if (needLock) {
                showInProgress(getString(R.string.in_progress_my))
                lockScreenOrientation()
            }

            reloadThumbnailsFromPlp(
                onStarted = {
                    hideInProgressIfShown()
                    unlockScreenOrientation()
                    spLock.edit { putBoolean(KEY_RESTORE_LOCK_UNTIL_RELOAD, false) }
                }
            )
        }

        savedInstanceState?.getIntegerArrayList("state_selected_idx")?.let { restoredIdx ->
            if (restoredIdx.isNotEmpty()) {
                val max = viewModel.pageItems.size
                restoredIdx.forEach { i ->
                    if (i in 0 until max) {
                        viewModel.pageItems[i].isSelected = true
                        adapter.notifyItemChanged(i)
                    }
                }
            }
        }

        updateButtonsState()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAndRemoveTask()
            }
        })
    }

    private fun clearCacheDir() {
        try {
            cacheDir.listFiles()?.forEach { file -> file.deleteRecursively() }
        } catch (_: Exception) { }
    }

    private fun sourceTempFile(): File = File(cacheDir, SOURCE_TEMP_NAME)
    private fun sourceWorkFile(): File = File(cacheDir, SOURCE_TEMP_WORK)
    private fun plpTempFile(): File = File(cacheDir, PLP_TEMP_NAME)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val selectedIdx = viewModel.pageItems
            .mapIndexedNotNull { idx, it -> if (it.isSelected) idx else null }
        if (selectedIdx.isNotEmpty()) {
            outState.putIntegerArrayList("state_selected_idx", ArrayList(selectedIdx))
        }

        if (isChangingConfigurations) {
            runCatching {
                val lm = binding.rvThumbnails.layoutManager as? LinearLayoutManager
                val first = lm?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                val offset = if (first != RecyclerView.NO_POSITION) {
                    lm?.findViewByPosition(first)?.top ?: 0
                } else 0

                val nsv = findViewById<NestedScrollView>(R.id.root_scroll)
                val nsvY = nsv?.scrollY ?: -1

                val csv = selectedIdx.joinToString(",")

                val sp = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
                sp.edit {
                    putInt(KEY_THEME_RESTORE_FIRST, first)
                    putInt(KEY_THEME_RESTORE_OFFSET, offset)
                    putString(KEY_THEME_RESTORE_SELECTED_CSV, csv)
                    putInt(KEY_THEME_RESTORE_NSV_Y, nsvY)
                    putBoolean(KEY_RESTORE_LOCK_UNTIL_RELOAD, true)
                }
            }.onFailure {
            }
        }
    }

    private fun lockScreenOrientation() {
        if (orientationLockPrevRequested != null) return

        orientationLockPrevRequested = requestedOrientation

        val lockTo = when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Configuration.ORIENTATION_PORTRAIT  ->
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
        requestedOrientation = lockTo
    }

    private fun unlockScreenOrientation() {
        val prev = orientationLockPrevRequested ?: return
        orientationLockPrevRequested = null
        requestedOrientation = prev
    }

    private fun sourceTempPageCount(): Int {
        val f = sourceTempFile()
        if (!f.exists()) return 0
        return try { PdfDocument(rdr(f.absolutePath)).use { it.numberOfPages } } catch (_: Exception) { 0 }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleIncomingShareIntent(intent)

        setIntent(Intent())
    }

    private fun handleIncomingShareIntent(intent: Intent) {
        val action = intent.action

        val collected = LinkedHashSet<Uri>()

        fun tryCollectIfLikelyPdf(uri: Uri?) {
            if (uri == null) return

            val mt = try { contentResolver.getType(uri) } catch (_: Exception) { null }
            val mtLc = mt?.lowercase(Locale.ROOT)
            val nameLc = try { extractFileName(uri).lowercase(Locale.ROOT) } catch (_: Exception) { "" }

            val explicitNonPdf =
                (mtLc?.startsWith("image/") ?: false) ||
                        (mtLc?.startsWith("text/")  ?: false) ||
                        (mtLc?.startsWith("audio/") ?: false) ||
                        (mtLc?.startsWith("video/") ?: false)
            if (explicitNonPdf) return

            val isPdfMime = mtLc?.contains("pdf") ?: false
            val isPdfName = nameLc.endsWith(".pdf")

            val isUnknownApp = when (mtLc) {
                null -> true
                "application/octet-stream" -> true
                else -> mtLc.startsWith("application/")
            }

            val looksPdf = isPdfMime || isPdfName || isUnknownApp
            if (looksPdf) collected.add(uri)
        }

        when (action) {
            Intent.ACTION_VIEW -> {
                tryCollectIfLikelyPdf(intent.data)
                intent.clipData?.let { cd ->
                    for (i in 0 until cd.itemCount) tryCollectIfLikelyPdf(cd.getItemAt(i)?.uri)
                }
            }
            Intent.ACTION_SEND -> {
                tryCollectIfLikelyPdf(intent.getParcelableCompat(Intent.EXTRA_STREAM))
                intent.clipData?.let { cd ->
                    for (i in 0 until cd.itemCount) tryCollectIfLikelyPdf(cd.getItemAt(i)?.uri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = intent.getParcelableArrayListCompat(Intent.EXTRA_STREAM)
                if (list != null && list.isNotEmpty()) {
                    for (u in list) tryCollectIfLikelyPdf(u)
                } else {
                    val cd = intent.clipData
                    if (cd != null && cd.itemCount > 0) {
                        for (i in 0 until cd.itemCount) tryCollectIfLikelyPdf(cd.getItemAt(i)?.uri)
                    } else {
                        tryCollectIfLikelyPdf(intent.getParcelableCompat(Intent.EXTRA_STREAM))
                    }
                }
            }
        }

        if (collected.isEmpty()) {
            when (action) {
                Intent.ACTION_VIEW -> {
                    val single = intent.data
                    if (single != null) {
                        val mtLc = try { contentResolver.getType(single)?.lowercase(Locale.ROOT) } catch (_: Exception) { null }
                        val nameLc = try { extractFileName(single).lowercase(Locale.ROOT) } catch (_: Exception) { "" }
                        if (isSupportedImageMime(mtLc) || isSupportedImageFileName(nameLc)) {
                            run {
                                val decor = window?.decorView
                                val isWarmStart = try { decor?.isShown == true && hasWindowFocus() } catch (_: Exception) { false }
                                if (isWarmStart) {
                                    showInProgress(getString(R.string.in_progress_my))
                                    lockScreenOrientation()
                                    handlePdfUri(single)
                                } else {
                                    decor?.post {
                                        showInProgress(getString(R.string.in_progress_my))
                                        lockScreenOrientation()
                                        handlePdfUri(single)
                                    } ?: run {
                                        showInProgress(getString(R.string.in_progress_my))
                                        lockScreenOrientation()
                                        handlePdfUri(single)
                                    }
                                }
                            }
                            return
                        }else {
                            val looksImageByMime = (mtLc?.startsWith("image/") == true)

                            if (looksImageByMime) {
                                Toast.makeText(this@MainActivity, getString(R.string.unsupported_image), Toast.LENGTH_LONG).show()
                                return
                            }
                        }
                    }
                }
                Intent.ACTION_SEND -> {
                    val single = intent.getParcelableCompat(Intent.EXTRA_STREAM)
                    if (single != null) {
                        val mtLc = try { contentResolver.getType(single)?.lowercase(Locale.ROOT) } catch (_: Exception) { null }
                        val nameLc = try { extractFileName(single).lowercase(Locale.ROOT) } catch (_: Exception) { ""
                        }
                        if (isSupportedImageMime(mtLc) || isSupportedImageFileName(nameLc)) {
                            run {
                                val decor = window?.decorView
                                val isWarmStart = try { decor?.isShown == true && hasWindowFocus() } catch (_: Exception) { false }
                                if (isWarmStart) {
                                    showInProgress(getString(R.string.in_progress_my))
                                    lockScreenOrientation()
                                    handlePdfUri(single)
                                } else {
                                    decor?.post {
                                        showInProgress(getString(R.string.in_progress_my))
                                        lockScreenOrientation()
                                        handlePdfUri(single)
                                    } ?: run {
                                        showInProgress(getString(R.string.in_progress_my))
                                        lockScreenOrientation()
                                        handlePdfUri(single)
                                    }
                                }
                            }
                            return
                        }else {
                            val looksImageByMime = (mtLc?.startsWith("image/") == true)

                            if (looksImageByMime) {
                                Toast.makeText(this@MainActivity, getString(R.string.unsupported_image), Toast.LENGTH_LONG).show()
                                return
                            }
                        }
                    }
                }
            }
            return
        }

        if (collected.isEmpty()) return

        if (collected.size == 1) {
            val only = collected.first()
            run {
                val decor = window?.decorView
                val isWarmStart = try { decor?.isShown == true && hasWindowFocus() } catch (_: Exception) { false }
                if (isWarmStart) {
                    showInProgress(getString(R.string.in_progress_my))
                    lockScreenOrientation()
                    handlePdfUri(only)
                } else {
                    decor?.post {
                        showInProgress(getString(R.string.in_progress_my))
                        lockScreenOrientation()
                        handlePdfUri(only)
                    } ?: run {
                        showInProgress(getString(R.string.in_progress_my))
                        lockScreenOrientation()
                        handlePdfUri(only)
                    }
                }
            }
        } else {
            run {
                val decor = window?.decorView
                val isWarmStart = try { decor?.isShown == true && hasWindowFocus() } catch (_: Exception) { false }
                if (isWarmStart) {
                    showInProgress(getString(R.string.in_progress_my))
                    lockScreenOrientation()
                    handlePdfUrisBatch(collected.toList())
                } else {
                    decor?.post {
                        showInProgress(getString(R.string.in_progress_my))
                        lockScreenOrientation()
                        handlePdfUrisBatch(collected.toList())
                    } ?: run {
                        showInProgress(getString(R.string.in_progress_my))
                        lockScreenOrientation()
                        handlePdfUrisBatch(collected.toList())
                    }
                }
            }
        }
    }

    private fun handlePdfUrisBatch(uris: List<Uri>) {
        if (uris.isEmpty()) return

        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        val selectedIdx = viewModel.pageItems
            .mapIndexedNotNull { idx, it -> if (it.isSelected) idx else null }

        val oldPer = run {
            val last = lastBuiltNupConfig ?: currentNupConfigFromInputs()
            (last.rows.coerceAtLeast(1)) * (last.cols.coerceAtLeast(1))
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val oldPageCount = sourceTempPageCount()

                val selectedSrcPages: Set<Int> = if (selectedIdx.isNotEmpty() && oldPageCount > 0) {
                    buildSet {
                        selectedIdx.forEach { g ->
                            val start = g * oldPer + 1
                            val end = min(oldPageCount, start + oldPer - 1)
                            for (p in start..end) add(p)
                        }
                    }
                } else emptySet()

                fun isBadPassword(t: Throwable): Boolean {
                    if (t is BadPasswordException) return true
                    val msg = t.message?.lowercase().orEmpty()
                    return msg.contains("bad user password") || msg.contains("bad password")
                }

                for (uri in uris) {
                    val tempSrc = File(cacheDir, "${timeStamp}_${extractFileName(uri)}")
                    try {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            FileOutputStream(tempSrc).use { outputStream ->
                                val buffer = ByteArray(4096)
                                var bytesRead: Int
                                while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                            }
                        } ?: throw IOException(getString(R.string.input_stream_is_null))

                        var passwordBytes: ByteArray? = null
                        var attempt = 0

                        while (true) {
                            try {
                                appendPdfToSourceTemp(tempSrc, passwordBytes)
                                break
                            } catch (e: Exception) {
                                if (!isBadPassword(e)) throw e

                                withContext(Dispatchers.Main) {
                                    hideInProgressIfShown()
                                }
                                val entered: String? = withContext(Dispatchers.Main) {
                                    promptForPdfPassword(showError = (attempt > 0))
                                }
                                if (entered.isNullOrEmpty()) {
                                    withContext(Dispatchers.Main) {
                                    }
                                    tempSrc.delete()
                                    return@launch
                                } else {
                                    passwordBytes = entered.toByteArray(Charsets.UTF_8)
                                    attempt++
                                    withContext(Dispatchers.Main) { showInProgress(getString(R.string.in_progress_my)) }
                                }
                            }
                        }
                    } finally {
                        runCatching { tempSrc.delete() }
                    }
                }

                createTempPdfInternal()

                withContext(Dispatchers.Main) {
                    resetThumbDocKey("handlePdfUrisBatch")

                    val per = perGroup()
                    val indicesToSelectAfter: Set<Int> =
                        if (selectedSrcPages.isNotEmpty())
                            selectedSrcPages.map { p -> (p - 1) / per }.toSet()
                        else emptySet()

                    val onReloadStarted: () -> Unit = {
                        hideInProgressIfShown()
                        unlockScreenOrientation()
                    }

                    reloadThumbnailsFromPlp(
                        targetIndex = null,
                        targetOffsetPx = 0,
                        restorePrevious = true,
                        indicesToSelect = indicesToSelectAfter,
                        releaseUiAtStart = false,
                        onStarted = onReloadStarted
                    )

                    updateButtonsState()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.error_loading_pdf_with_msg, e.message), Toast.LENGTH_LONG).show()
                    hideInProgressIfShown()
                    unlockScreenOrientation()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    unlockScreenOrientation()
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        var shouldHideKeyboard = false
        var targetWindowToken: IBinder? = null

        var focusedBefore: View? = null

        var lastUpX = -1f
        var lastUpY = -1f

        if (ev.action == MotionEvent.ACTION_UP) {
            currentFocus?.let { focusedView ->
                if (focusedView is EditText) {
                    val outRect = Rect()
                    focusedView.getGlobalVisibleRect(outRect)
                    if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                        shouldHideKeyboard = true
                        targetWindowToken = focusedView.windowToken
                        focusedBefore = focusedView
                        lastUpX = ev.rawX
                        lastUpY = ev.rawY
                    }
                }
            }
        }

        val handled = super.dispatchTouchEvent(ev)

        val newFocus = currentFocus

        val switchingBetweenEditTexts = shouldHideKeyboard &&
                (focusedBefore is EditText) &&
                (newFocus is EditText) &&
                (newFocus !== focusedBefore) &&
                run {
                    if (lastUpX < 0f || lastUpY < 0f) {
                        false
                    } else {
                        val r = Rect()
                        newFocus.getGlobalVisibleRect(r)
                        r.contains(lastUpX.toInt(), lastUpY.toInt())
                    }
                }

        if (switchingBetweenEditTexts) {
            shouldHideKeyboard = false

            val targetEdit: EditText = newFocus

            window?.decorView?.post {
                try {
                    if (!targetEdit.isFocused) {
                        targetEdit.requestFocus()
                    }
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(targetEdit, InputMethodManager.SHOW_IMPLICIT)
                } catch (_: Exception) { }
            }
        }

        if (shouldHideKeyboard) {
            window?.decorView?.post {
                try {
                    focusedBefore?.let { fv ->
                        if (fv.isFocused) {
                            fv.clearFocus()
                        }
                    }

                    run {
                        val root = findViewById<View>(android.R.id.content)
                        if (root != null) {
                            val prevFocusable = root.isFocusableInTouchMode
                            root.isFocusableInTouchMode = true
                            root.requestFocus()
                            root.post {
                                try {
                                    root.isFocusableInTouchMode = prevFocusable
                                } catch (_: Exception) { }
                            }
                        }
                    }

                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    val token = targetWindowToken ?: window?.decorView?.windowToken
                    if (token != null) {
                        imm.hideSoftInputFromWindow(token, 0)
                    }
                } catch (_: Exception) { }
            }
        }

        return handled
    }

    private fun numberRangeFilterNoOp(range: IntRange) = InputFilter { src, start, end, dest, dStart, dEnd ->
        val newText = dest.take(dStart).toString() + src.subSequence(start, end) + dest.drop(dEnd).toString()
        if (newText.isEmpty()) return@InputFilter null
        val v = newText.toIntOrNull()
        if (v != null && v in range) null else dest.subSequence(dStart, dEnd)
    }

    private fun updateSignatureInputFilters() {
        signaturePosX.filters = arrayOf(InputFilter.LengthFilter(3))
        signaturePosY.filters = arrayOf(InputFilter.LengthFilter(3))

        validateInputs()
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) for (child in children) if (!deleteDir(File(dir, child))) return false
            return dir.delete()
        } else if (dir != null && dir.isFile) {
            return dir.delete()
        }
        return false
    }

    private fun showMaxCoordinatesAlert(maxXi: Int, maxYi: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_signature_chooser, null)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.signature_popup_title)?.visibility = View.GONE

        val listView = dialogView.findViewById<ListView>(R.id.listView)
        val parent = listView.parent as ViewGroup
        val indexInParent = parent.indexOfChild(listView)
        val lp = listView.layoutParams
        val padStart = listView.paddingLeft
        val padTop = listView.paddingTop
        val padEnd = listView.paddingRight
        val padBottom = listView.paddingBottom
        parent.removeView(listView)

        val messageView = MaterialTextView(dialogView.context).apply {
            layoutParams = lp
            text = getString(R.string.max_coordinates_x_y_mm, maxXi, maxYi)

            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER

            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_PdfLabelPrinting_ListItem)
            setPadding(padStart, padTop, padEnd, padBottom)
        }

        runCatching {
            val extraTop = resources.getDimensionPixelSize(R.dimen.dp_20)
            when (val orig = messageView.layoutParams) {
                is LinearLayout.LayoutParams -> {
                    val n = LinearLayout.LayoutParams(orig)
                    n.topMargin = orig.topMargin + extraTop
                    messageView.layoutParams = n
                }
                is ViewGroup.MarginLayoutParams -> {
                    val n = ViewGroup.MarginLayoutParams(orig)
                    n.topMargin = orig.topMargin + extraTop
                    messageView.layoutParams = n
                }
                else -> {
                    val n = ViewGroup.MarginLayoutParams(orig)
                    n.topMargin = extraTop
                    messageView.layoutParams = n
                }
            }
        }

        parent.addView(messageView, indexInParent)

        val btnNew = dialogView.findViewById<Button>(R.id.btn_new)
        val btnRemove = dialogView.findViewById<Button>(R.id.btn_remove)
        val btnOk = dialogView.findViewById<Button>(R.id.btn_ok)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

        btnNew?.visibility = View.GONE
        btnRemove?.visibility = View.GONE
        btnCancel?.visibility = View.GONE

        (btnOk?.layoutParams as? LinearLayout.LayoutParams)?.let { lpOk ->
            lpOk.width = ViewGroup.LayoutParams.WRAP_CONTENT
            lpOk.weight = 0f
            lpOk.gravity = Gravity.CENTER_HORIZONTAL
            btnOk.layoutParams = lpOk
        }
        btnOk?.setOnClickListener { dialog.dismiss() }

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    private fun refPxForSignatureDpi(idx: Int): Float {
        return when (idx) {
            0 -> 1190f
            1 -> 2480f
            else -> 1190f
        }
    }

    private fun currentSignatureRefPx(): Float = refPxForSignatureDpi(selectedDpiIndexForSignature)

    private fun showSignatureDialog() {
        if (viewModel.pageItems.none { it.isSelected }) {
            Toast.makeText(this, getString(R.string.select_a_page), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            val signatureFiles = withContext(Dispatchers.IO) {
                (signaturesDir.listFiles { file ->
                    file.isFile &&
                            file.name.startsWith("sign_") &&
                            file.name.endsWith(".png", ignoreCase = true)
                }?.toList() ?: emptyList()).sortedBy { it.lastModified() }
            }

            val sigDisplayNames = signatureFiles.map { file ->
                file.name.removePrefix("sign_").removeSuffix(".png").replace('_', ' ')
            }.toTypedArray()

            selectedSigIndex = -1

            val prefs = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
            val lastDpiSaved = prefs.getInt(KEY_LAST_SIG_DPI_INDEX, 0).coerceIn(0, 1)
            selectedDpiIndexForSignature = lastDpiSaved

            val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_signature_chooser, null)
            val builder = MaterialAlertDialogBuilder(this@MainActivity).setView(dialogView)
            val dialog = builder.create()

            val listView = dialogView.findViewById<ListView>(R.id.listView)

            var applyScanEffect = prefs.getBoolean(KEY_LAST_SCAN_EFFECT, false)
            var applyGrayscalePage = prefs.getBoolean(KEY_LAST_GRAYSCALE, false)

            var overrideXmm: Float? = null
            var overrideYmm: Float? = null

            var dpiSelectedIndex = selectedDpiIndexForSignature

            run {
                data class RowViews(
                    val container: LinearLayout,
                    val text: MaterialTextView,
                    val rightGroup: LinearLayout?,
                    val radio: MaterialRadioButton?,
                    val edit: AppCompatImageButton?,
                    val scanCheck: MaterialCheckBox?,
                    val type: Int
                )

                val customAdapter = object : BaseAdapter() {
                    override fun getCount(): Int = sigDisplayNames.size + 4

                    override fun getItem(position: Int): Any =
                        when (position) {
                            0 -> getString(R.string.dpi_144)
                            1 -> getString(R.string.dpi_300)
                            2 -> getString(R.string.scan_effect)
                            3 -> getString(R.string.print_page_grayscale)
                            else -> sigDisplayNames[position - 4]
                        }

                    override fun getItemId(position: Int): Long = position.toLong()

                    @SuppressLint("RtlHardcoded")
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val isDpiRow = (position == 0 || position == 1)
                        val isScanRow = (position == 2)
                        val isGrayRow = (position == 3)
                        val isSignatureRow = (position >= 4)

                        val desiredType = when {
                            isDpiRow -> 0
                            isScanRow || isGrayRow -> 1
                            else -> 2
                        }

                        val row = if (convertView == null || ((convertView.tag as? RowViews)?.type != desiredType)) {
                            val container = LinearLayout(parent.context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )

                                val padH = parent.resources.getDimensionPixelSize(R.dimen.dp_16)
                                val padV = parent.resources.getDimensionPixelSize(R.dimen.dp_12)
                                setPadding(
                                    padH,
                                    padV,
                                    padH - (padH - parent.resources.getDimensionPixelSize(R.dimen.dp_12)),
                                    padV
                                )
                                isClickable = true
                                isFocusable = true
                            }

                            val tv = MaterialTextView(parent.context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                                )
                                textAlignment = View.TEXT_ALIGNMENT_GRAVITY
                                gravity = Gravity.LEFT
                                TextViewCompat.setTextAppearance(
                                    this, R.style.TextAppearance_PdfLabelPrinting_ListItem
                                )
                            }

                            if (isScanRow || isGrayRow) {
                                val rightGroup = LinearLayout(parent.context).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        gravity = Gravity.RIGHT
                                    }
                                    isBaselineAligned = false
                                }

                                val chk = MaterialCheckBox(parent.context).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        gravity = Gravity.RIGHT
                                    }
                                    contentDescription = null
                                    background = null
                                    setPadding(0, 0, 0, 0)

                                    isUseMaterialThemeColors = false
                                    buttonDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_checkbox)
                                    buttonTintList = AppCompatResources.getColorStateList(context, R.color.ic_checkbox_filename_color)

                                    isChecked = applyScanEffect

                                    isClickable = false
                                    isFocusable = false

                                    minWidth = 0
                                    minHeight = 0
                                }

                                run {
                                    val rbProbe = MaterialRadioButton(parent.context)
                                    val rbW = (rbProbe.buttonDrawable?.intrinsicWidth ?: 0)
                                    val chkW = (chk.buttonDrawable?.intrinsicWidth ?: 0)
                                    val extraEnd = (rbW - chkW)
                                    if (extraEnd > 0) {
                                        rightGroup.setPadding(0, 0, extraEnd, 0)
                                    }
                                }

                                rightGroup.addView(chk)
                                container.addView(tv)
                                container.addView(rightGroup)

                                container.tag = RowViews(
                                    container = container,
                                    text = tv,
                                    rightGroup = rightGroup,
                                    radio = null,
                                    edit = null,
                                    scanCheck = chk,
                                    type = 1
                                )
                                container
                            } else if (isDpiRow) {
                                val rightGroup = LinearLayout(parent.context).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        gravity = Gravity.RIGHT
                                    }
                                    isBaselineAligned = false
                                }

                                val rb = MaterialRadioButton(parent.context).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        gravity = Gravity.RIGHT
                                    }
                                    isClickable = false
                                    isFocusable = false
                                    setPadding(0, 0, 0, 0)
                                    minWidth = 0
                                    minHeight = 0
                                }

                                rightGroup.addView(rb)
                                container.addView(tv)
                                container.addView(rightGroup)

                                container.tag = RowViews(
                                    container = container,
                                    text = tv,
                                    rightGroup = rightGroup,
                                    radio = rb,
                                    edit = null,
                                    scanCheck = null,
                                    type = 0
                                )
                                container
                            } else {
                                val rightGroup = LinearLayout(parent.context).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        gravity = Gravity.RIGHT
                                    }
                                    isBaselineAligned = false
                                }

                                val rb = MaterialRadioButton(parent.context).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        gravity = Gravity.RIGHT
                                    }
                                    isClickable = false
                                    isFocusable = false
                                    setPadding(0, 0, 0, 0)
                                    minWidth = 0
                                    minHeight = 0
                                }

                                val editBtn = AppCompatImageButton(parent.context).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        leftMargin = 0
                                        gravity = Gravity.RIGHT
                                        gravity = Gravity.CENTER_VERTICAL
                                    }
                                    setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_edit_24))
                                    contentDescription = null
                                    background = null
                                    isFocusable = false
                                    setPadding(0, 0, 0, 0)
                                }

                                rightGroup.addView(rb)
                                rightGroup.addView(editBtn)
                                container.addView(tv)
                                container.addView(rightGroup)

                                container.tag = RowViews(
                                    container = container,
                                    text = tv,
                                    rightGroup = rightGroup,
                                    radio = rb,
                                    edit = editBtn,
                                    scanCheck = null,
                                    type = 2
                                )
                                container
                            }
                        } else convertView

                        val holder = row.tag as RowViews

                        holder.text.text = when {
                            isDpiRow && position == 0 -> getString(R.string.dpi_144)
                            isDpiRow -> getString(R.string.dpi_300)
                            isScanRow -> getString(R.string.scan_effect)
                            isGrayRow -> getString(R.string.print_page_grayscale)
                            else -> sigDisplayNames[position - 4]
                        }

                        run {
                            val checkedPos = listView.checkedItemPosition
                            if (isSignatureRow) {
                                holder.radio?.isEnabled = true

                                holder.radio?.isChecked = (position == checkedPos)
                                val onePageSelected = (viewModel.pageItems.count { it.isSelected } == 1)
                                val isThisSigSelected = (position == checkedPos)
                                val canEdit = onePageSelected && isThisSigSelected
                                holder.edit?.isEnabled = canEdit

                                holder.edit?.setOnClickListener {
                                    if (!canEdit) return@setOnClickListener

                                    val singleIdx = viewModel.pageItems
                                        .mapIndexedNotNull { idx, it -> if (it.isSelected) idx else null }
                                        .firstOrNull() ?: return@setOnClickListener

                                    lifecycleScope.launch(Dispatchers.Main) {
                                        val sigFile = signatureFiles[position - 4]
                                        val (pxW, pxH) = withContext(Dispatchers.IO) {
                                            readImageBounds(sigFile.absolutePath)
                                        }

                                        if (pxW <= 0 || pxH <= 0) {
                                            Toast.makeText(this@MainActivity, getString(R.string.signature_image_invalid), Toast.LENGTH_LONG).show()
                                            return@launch
                                        }

                                        val isLand = landscape.isChecked
                                        val a4Wmm  = if (isLand) 297f else 210f
                                        val a4Hmm  = if (isLand) 210f else 297f
                                        val refAxisMm = if (isLand) a4Hmm else a4Wmm
                                        val mmPerSigPx = refAxisMm / refPxForSignatureDpi(dpiSelectedIndex)
                                        val imgWmm = pxW * mmPerSigPx
                                        val imgHmm = pxH * mmPerSigPx

                                        val maxXf = a4Wmm - imgWmm
                                        val maxYf = a4Hmm - imgHmm
                                        val maxXi = floor(maxXf).toInt()
                                        val maxYi = floor(maxYf).toInt()

                                        if (maxXf < 0f || maxYf < 0f) {
                                            showMaxCoordinatesAlert(maxXi, maxYi)
                                            return@launch
                                        }

                                        runCatching {
                                            openSignaturePositionEditor(
                                                signatureFile = sigFile,
                                                plpPageIndex = singleIdx
                                            ) { xmm, ymm ->
                                                if (xmm != null && ymm != null) {
                                                    overrideXmm = xmm
                                                    overrideYmm = ymm
                                                }
                                            }
                                        }.onFailure {
                                            Toast.makeText(this@MainActivity, getString(R.string.editor_open_error), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            } else if (isScanRow) {
                                holder.scanCheck?.isChecked = applyScanEffect
                            } else if (isGrayRow) {
                                holder.scanCheck?.isChecked = applyGrayscalePage
                            } else if (isDpiRow) {
                                holder.radio?.isChecked = (dpiSelectedIndex == position)
                            }
                        }

                        holder.container.setOnClickListener {
                            if (isDpiRow) {
                                dpiSelectedIndex = position
                                selectedDpiIndexForSignature = position
                                prefs.edit {
                                    putInt(KEY_LAST_SIG_DPI_INDEX, position)
                                }
                                (listView.adapter as? BaseAdapter)?.notifyDataSetChanged()
                            } else if (isScanRow) {
                                applyScanEffect = !applyScanEffect
                                (row.tag as RowViews).scanCheck?.isChecked = applyScanEffect
                                prefs.edit {
                                    putBoolean(KEY_LAST_SCAN_EFFECT, applyScanEffect)
                                }
                                (listView.adapter as? BaseAdapter)?.notifyDataSetChanged()
                            } else if (isGrayRow) {
                                applyGrayscalePage = !applyGrayscalePage
                                (row.tag as RowViews).scanCheck?.isChecked = applyGrayscalePage
                                prefs.edit {
                                    putBoolean(KEY_LAST_GRAYSCALE, applyGrayscalePage)
                                }
                                (listView.adapter as? BaseAdapter)?.notifyDataSetChanged()
                            } else {
                                listView.setItemChecked(position, true)
                                (listView.adapter as? BaseAdapter)?.notifyDataSetChanged()
                                listView.performItemClick(row, position, listView.getItemIdAtPosition(position))
                            }
                        }

                        return row
                    }
                }

                listView.adapter = customAdapter
                listView.choiceMode = ListView.CHOICE_MODE_SINGLE
            }

            val btnNew = dialogView.findViewById<Button>(R.id.btn_new)
            val btnRemove = dialogView.findViewById<Button>(R.id.btn_remove)
            val btnOk = dialogView.findViewById<Button>(R.id.btn_ok)
            val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

            btnNew.setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/png"
                }
                signaturePickerLauncher.launch(intent)
                dialog.dismiss()
            }

            btnRemove.isEnabled = false
            btnRemove.setOnClickListener {
                val checkedPosition = listView.checkedItemPosition
                if (checkedPosition != ListView.INVALID_POSITION && checkedPosition >= 4) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val fileToDelete = signatureFiles[checkedPosition - 4]
                        val deleted = fileToDelete.delete()
                        withContext(Dispatchers.Main) {
                            if (deleted) {
                                showSignatureDialog()
                                dialog.dismiss()
                            } else {
                                Toast.makeText(this@MainActivity, getString(R.string.failed_to_delete_signature), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            btnOk.isEnabled = false
            btnOk.setOnClickListener {
                val pos = listView.checkedItemPosition
                if (pos == ListView.INVALID_POSITION || pos < 4) return@setOnClickListener

                val sigFile = signatureFiles[pos - 4]

                val xmmFromEditor = overrideXmm
                val ymmFromEditor = overrideYmm

                val xStr = signaturePosX.text?.toString()?.trim().orEmpty()
                val yStr = signaturePosY.text?.toString()?.trim().orEmpty()
                val inputXmm = xmmFromEditor ?: (if (xStr.isEmpty()) 0f else xStr.toFloatOrNull())
                val inputYmm = ymmFromEditor ?: (if (yStr.isEmpty()) 0f else yStr.toFloatOrNull())

                if (inputXmm == null || inputYmm == null) {
                    Toast.makeText(this@MainActivity, getString(R.string.enter_value_0_100), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch(Dispatchers.Main) {
                    val (pxW, pxH) = withContext(Dispatchers.IO) {
                        readImageBounds(sigFile.absolutePath)
                    }

                    if (pxW <= 0 || pxH <= 0) {
                        Toast.makeText(this@MainActivity, getString(R.string.signature_image_invalid), Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val isLand = landscape.isChecked
                    val a4Wmm  = if (isLand) 297f else 210f
                    val a4Hmm  = if (isLand) 210f else 297f
                    val refAxisMm = if (isLand) a4Hmm else a4Wmm
                    val mmPerSigPx = refAxisMm / refPxForSignatureDpi(dpiSelectedIndex)
                    val imgWmm = pxW * mmPerSigPx
                    val imgHmm = pxH * mmPerSigPx

                    val maxXf = a4Wmm - imgWmm
                    val maxYf = a4Hmm - imgHmm
                    val maxXi = floor(maxXf).toInt()
                    val maxYi = floor(maxYf).toInt()

                    if (inputXmm !in 0f..maxXf || inputYmm !in 0f..maxYf) {
                        currentSigFile = null

                        signaturePosX.filters = arrayOf(InputFilter.LengthFilter(3))
                        signaturePosY.filters = arrayOf(InputFilter.LengthFilter(3))
                        dialog.dismiss()
                        window?.decorView?.post {
                            showMaxCoordinatesAlert(maxXi, maxYi)
                        }
                        return@launch
                    }

                    signSelectedPagesOnSourceTemp(
                        signatureFilePath = sigFile.absolutePath,
                        inputXMM = inputXmm,
                        inputYMM = inputYmm,
                        applyScanEffect = applyScanEffect,
                        applyGrayscalePage = applyGrayscalePage
                    )

                    dialog.dismiss()
                }
            }

            listView.setOnItemClickListener { _, _, position, _ ->
                if (position >= 4) currentSigFile = signatureFiles[position - 4]

                btnRemove.isEnabled = (position >= 4)
                btnOk.isEnabled     = false

                lifecycleScope.launch(Dispatchers.Main) {
                    if (position >= 4) {
                        val file = currentSigFile
                        val (pxW, pxH) = withContext(Dispatchers.IO) {
                            readImageBounds(file!!.absolutePath)
                        }

                        if (pxW > 0 && pxH > 0) {
                            signaturePosX.filters = arrayOf(InputFilter.LengthFilter(3))
                            signaturePosY.filters = arrayOf(InputFilter.LengthFilter(3))
                        }

                        btnOk.isEnabled = true

                        (listView.adapter as? BaseAdapter)?.notifyDataSetChanged()
                    } else {
                        (listView.adapter as? BaseAdapter)?.notifyDataSetChanged()

                        btnOk.isEnabled = false
                    }
                }
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }
    }

    private class DraggableSignatureView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
    ) : AppCompatImageView(context, attrs) {

        var minLeft: Int = 0
        var minTop: Int = 0
        var maxLeft: Int = Int.MAX_VALUE
        var maxTop: Int = Int.MAX_VALUE

        var onMoved: ((left: Int, top: Int) -> Unit)? = null

        private var dX = 0f
        private var dY = 0f

        private var downXRelToParent = 0f
        private var downYRelToParent = 0f

        private val clickSlop = ViewConfiguration.get(context).scaledTouchSlop

        fun updatePosition(left: Int, top: Int) {
            layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = left
                topMargin = top
            }
            requestLayout()
            onMoved?.invoke(left, top)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            fun parentLocationOnScreen(): Pair<Int, Int> {
                val loc = IntArray(2)
                (parent as? View)?.getLocationOnScreen(loc)
                return loc[0] to loc[1]
            }

            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val lp = layoutParams as FrameLayout.LayoutParams
                    val (parentX, parentY) = parentLocationOnScreen()

                    dX = ev.rawX - parentX - lp.leftMargin
                    dY = ev.rawY - parentY - lp.topMargin
                    downXRelToParent = ev.rawX - parentX
                    downYRelToParent = ev.rawY - parentY

                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val (parentX, parentY) = parentLocationOnScreen()

                    val wantedLeft = (ev.rawX - parentX - dX).toInt()
                    val wantedTop  = (ev.rawY - parentY - dY).toInt()

                    val clampedLeft = wantedLeft.coerceIn(minLeft, maxLeft)
                    val clampedTop  = wantedTop.coerceIn(minTop,  maxTop)
                    updatePosition(clampedLeft, clampedTop)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    parent?.requestDisallowInterceptTouchEvent(false)

                    val (parentX, parentY) = parentLocationOnScreen()
                    val dx = kotlin.math.abs((ev.rawX - parentX) - downXRelToParent)
                    val dy = kotlin.math.abs((ev.rawY - parentY) - downYRelToParent)
                    if (dx <= clickSlop && dy <= clickSlop) {
                        performClick()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun openSignaturePositionEditor(
        signatureFile: File,
        plpPageIndex: Int,
        onResult: (Float?, Float?) -> Unit
    ) {
        val ctx = this

        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_signature_position, null)
        val dialog = MaterialAlertDialogBuilder(ctx, R.style.SignaturePosition_PdfLabelPrinting_AlertDialog)
            .setView(dialogView)
            .create()

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.sp_btn_cancel)
        val btnOk = dialogView.findViewById<MaterialButton>(R.id.sp_btn_ok)

        val spThumb = dialogView.findViewById<AppCompatImageView>(R.id.sp_thumbnail)
        val thumbParent = spThumb.parent as ViewGroup
        val indexInParent = thumbParent.indexOfChild(spThumb)
        thumbParent.removeView(spThumb)

        val scroller = NestedScrollView(ctx).apply {
            layoutParams = when (thumbParent) {
                is LinearLayout -> LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                else -> ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            isFillViewport = true
            clipToPadding = true
            clipChildren = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = true
        }
        thumbParent.addView(scroller, indexInParent)

        val stage = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 0)
            isClickable = true
            isFocusable = true
            clipToPadding = true
            clipChildren = true
        }
        scroller.addView(stage)

        var inner: FrameLayout? = null

        val pageView = spThumb.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val sigView = DraggableSignatureView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
        }

        val isLand = landscape.isChecked
        val a4Wmm = if (isLand) 297f else 210f
        val a4Hmm = if (isLand) 210f else 297f

        val initXmm = signaturePosX.text?.toString()?.trim()?.toFloatOrNull() ?: 0f
        val initYmm = signaturePosY.text?.toString()?.trim()?.toFloatOrNull() ?: 0f

        var okReady = false
        var pageLeft = 0
        var pageTop = 0
        var pxPerMMX = 0f
        var pxPerMMY = 0f
        var currentLeft = 0
        var currentTop = 0

        var allowedMaxXmm = 0f
        var allowedMaxYmm = 0f

        btnCancel.setOnClickListener {
            dialog.dismiss()
            onResult(null, null)
        }
        btnOk.setOnClickListener {
            if (!okReady) {
                Toast.makeText(ctx, getString(R.string.in_progress_my), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val leftWithin = (currentLeft - pageLeft).toFloat()
            val topWithin  = (currentTop  - pageTop).toFloat()
            val outXmmRaw = leftWithin / pxPerMMX
            val outYmmRaw = topWithin  / pxPerMMY

            val outXmm = outXmmRaw.coerceIn(0f, allowedMaxXmm)
            val outYmm = outYmmRaw.coerceIn(0f, allowedMaxYmm)

            dialog.dismiss()
            onResult(outXmm, outYmm)
        }

        dialog.setOnShowListener {
            scroller.post {
                val contentRoot = runCatching {
                    dialog.window?.decorView?.findViewById<View>(android.R.id.content) as? ViewGroup
                }.getOrNull()

                var availW = stage.width
                if (availW <= 0) availW = scroller.width
                if (availW <= 0) availW = thumbParent.width
                if (availW <= 0) availW = dialogView.width
                if (availW <= 0) availW = contentRoot?.width ?: 0

                val pageSize = if (isLand) PageSize.A4.rotate() else PageSize.A4
                val pageAspect = pageSize.width / pageSize.height

                val contentW = availW.coerceAtLeast(1)
                val contentH = max(1, floor(contentW / pageAspect).toInt())

                val plp = plpTempFile()
                if (!plp.exists()) {
                    Toast.makeText(ctx, getString(R.string.temporary_pdf_not_found), Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    onResult(null, null)
                    return@post
                }

                val pageBmp = kotlin.run {
                    var bmp: Bitmap? = null
                    try {
                        ParcelFileDescriptor.open(plp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                if (plpPageIndex !in 0 until renderer.pageCount) return@use
                                renderer.openPage(plpPageIndex).use { page ->
                                    val b = createBitmap(contentW, contentH, Bitmap.Config.ARGB_8888)
                                    val sx = contentW.toFloat() / page.width.toFloat()
                                    val sy = contentH.toFloat() / page.height.toFloat()
                                    val mat = Matrix().apply { postScale(sx, sy) }
                                    page.render(b, null, mat, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bmp = b
                                }
                            }
                        }
                    } catch (_: Exception) { }
                    bmp
                }

                if (pageBmp == null) {
                    Toast.makeText(ctx, getString(R.string.failed_to_open_pdf_with_msg, "render"), Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    onResult(null, null)
                    return@post
                }

                if (inner == null) {
                    inner = FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            pageBmp.width,
                            pageBmp.height,
                            Gravity.CENTER
                        )
                        clipChildren = true
                        clipToPadding = true

                        val bg = ContextCompat.getColor(ctx, R.color.signature_position_pdf_bg)
                        setBackgroundColor(bg)
                    }
                    stage.addView(inner)
                } else {
                    inner.layoutParams = (inner.layoutParams as FrameLayout.LayoutParams).apply {
                        width = pageBmp.width
                        height = pageBmp.height
                        gravity = Gravity.CENTER
                    }
                }

                if (pageView.parent !== inner) {
                    (pageView.parent as? ViewGroup)?.removeView(pageView)
                    inner.addView(
                        pageView,
                        FrameLayout.LayoutParams(pageBmp.width, pageBmp.height, Gravity.CENTER)
                    )
                } else {
                    pageView.layoutParams = (pageView.layoutParams as FrameLayout.LayoutParams).apply {
                        width = pageBmp.width
                        height = pageBmp.height
                        gravity = Gravity.CENTER
                    }
                }
                pageView.setImageBitmap(pageBmp)
                pageView.requestLayout()

                val sigBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(signatureFile.absolutePath, sigBounds)

                val refAxisPx = if (isLand) pageBmp.height else pageBmp.width
                val desiredSigW = max(1, floor(sigBounds.outWidth  * (refAxisPx / currentSignatureRefPx())).toInt())
                val desiredSigH = max(1, floor(sigBounds.outHeight * (refAxisPx / currentSignatureRefPx())).toInt())

                fun calcInSampleSize(sw0: Int, sh0: Int, dw: Int, dh: Int): Int {
                    var sample = 1
                    val halfW = sw0 / 2
                    val halfH = sh0 / 2
                    while (halfW / sample >= dw && halfH / sample >= dh) sample *= 2
                    return sample.coerceAtLeast(1)
                }
                val sample = calcInSampleSize(sigBounds.outWidth, sigBounds.outHeight, desiredSigW, desiredSigH)
                val sigBmp = BitmapFactory.decodeFile(signatureFile.absolutePath, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })

                if (sigBmp == null) {
                    Toast.makeText(ctx, getString(R.string.signature_image_invalid), Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    onResult(null, null)
                    return@post
                }

                if (sigView.parent !== inner) {
                    (sigView.parent as? ViewGroup)?.removeView(sigView)
                    inner.addView(
                        sigView,
                        FrameLayout.LayoutParams(desiredSigW, desiredSigH).apply { gravity = Gravity.TOP or Gravity.LEFT }
                    )
                } else {
                    sigView.layoutParams = (sigView.layoutParams as FrameLayout.LayoutParams).apply {
                        width = desiredSigW
                        height = desiredSigH
                    }
                }
                sigView.setImageBitmap(sigBmp)
                sigView.alpha = 1f
                sigView.visibility = View.VISIBLE
                sigView.bringToFront()
                sigView.requestLayout()

                pxPerMMX = pageBmp.width.toFloat() / a4Wmm
                pxPerMMY = pageBmp.height.toFloat() / a4Hmm

                val refAxisMm = if (isLand) a4Hmm else a4Wmm
                val mmPerSigPx = refAxisMm / currentSignatureRefPx()
                val imgWmm = sigBounds.outWidth  * mmPerSigPx
                val imgHmm = sigBounds.outHeight * mmPerSigPx
                val rawMaxXmm = (a4Wmm - imgWmm).coerceAtLeast(0f)
                val rawMaxYmm = (a4Hmm - imgHmm).coerceAtLeast(0f)
                allowedMaxXmm = floor(rawMaxXmm)
                allowedMaxYmm = floor(rawMaxYmm)
                val allowedMaxLeftPx = floor(allowedMaxXmm * pxPerMMX).toInt()
                val allowedMaxTopPx  = floor(allowedMaxYmm * pxPerMMY).toInt()

                val initialLeftWithin = (initXmm * pxPerMMX).coerceIn(0f, allowedMaxLeftPx.toFloat())
                val initialTopWithin  = (initYmm * pxPerMMY).coerceIn(0f, allowedMaxTopPx.toFloat())

                pageLeft = 0
                pageTop  = 0

                currentLeft = initialLeftWithin.toInt()
                currentTop  = initialTopWithin.toInt()

                sigView.minLeft = 0
                sigView.minTop  = 0

                sigView.maxLeft = allowedMaxLeftPx
                sigView.maxTop  = allowedMaxTopPx

                sigView.onMoved = { l, t ->
                    currentLeft = l
                    currentTop  = t
                }

                sigView.updatePosition(currentLeft, currentTop)

                okReady = true
            }
        }

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    private fun readImageBounds(path: String): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        return (opts.outWidth to opts.outHeight)
    }

    private fun copySignatureToAppDir(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                var fileName = extractFileName(uri)
                if (!fileName.startsWith("sign_")) fileName = "sign_$fileName"
                if (!fileName.endsWith(".png", ignoreCase = true)) {
                    fileName = fileName.substringBeforeLast('.') + ".png"
                }

                val targetFile = File(signaturesDir, fileName)
                var uniqueFile = targetFile
                var counter = 1
                while (uniqueFile.exists()) {
                    val baseName = fileName.substringBeforeLast('.')
                    val extension = fileName.substringAfterLast('.', "png")
                    uniqueFile = File(signaturesDir, "${baseName}_${counter}.$extension"); counter++
                }

                FileOutputStream(uniqueFile).use { outputStream -> inputStream.copyTo(outputStream) }
                showSignatureDialog()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_copying_signature_with_msg, e.message), Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentSettings()
    }

    override fun onDestroy() {
        if (::progressDialog.isInitialized && progressDialog.isShowing) progressDialog.dismiss()
        saveCurrentSettings()
        (findViewById<RecyclerView>(R.id.rv_thumbnails).adapter as? ThumbnailAdapter)?.close()
        if (!isChangingConfigurations) clearCacheDir()
        super.onDestroy()
    }

    private fun snapshotInitialPrefsIfNeeded() {
        val sp = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)

        if (sp.getBoolean(KEY_FIRST_RUN_INITIALIZED, false)) return

        sp.edit {
            putString(KEY_ROWS, "1")
            putString(KEY_NORMAL_ROWS, "1")
            putString(KEY_LABEL_ROWS, "2")
            putString(KEY_COLUMNS, "1")
            putString(KEY_NORMAL_COLUMNS, "1")
            putString(KEY_LABEL_COLUMNS, "2")
            putString(KEY_BATCH_PAGE, "20")
            putString(KEY_NORMAL_BATCH_PAGE, "20")
            putString(KEY_LABEL_BATCH_PAGE, "20")
            putBoolean(KEY_SCALE, false)
            putBoolean(KEY_NORMAL_SCALE, false)
            putBoolean(KEY_LABEL_SCALE, true)
            putString(KEY_MARGIN, "0")
            putString(KEY_NORMAL_MARGIN, "0")
            putString(KEY_LABEL_MARGIN, "2")
            putBoolean(KEY_ORIENTATION, true)
            putBoolean(KEY_NORMAL_ORIENTATION, true)
            putBoolean(KEY_LABEL_ORIENTATION, true)
            putBoolean(KEY_DRAW_FRAME, false)
            putBoolean(KEY_NORMAL_DRAW_FRAME, false)
            putBoolean(KEY_LABEL_DRAW_FRAME, false)
            putString(KEY_SIG_POS_X, "155")
            putString(KEY_NORMAL_SIG_POS_X, "155")
            putString(KEY_LABEL_SIG_POS_X, "155")
            putString(KEY_SIG_POS_Y, "245")
            putString(KEY_NORMAL_SIG_POS_Y, "245")
            putString(KEY_LABEL_SIG_POS_Y, "245")
            putBoolean(KEY_PASSWORD_OUT, false)
            putBoolean(KEY_NORMAL_PASSWORD_OUT, false)
            putBoolean(KEY_LABEL_PASSWORD_OUT, false)
            putString(KEY_FILE_NAME_TEMPLATE, getString(R.string.default_tmpl_normal))
            putString(KEY_NORMAL_FILE_NAME_TEMPLATE, getString(R.string.default_tmpl_normal))
            putString(KEY_LABEL_FILE_NAME_TEMPLATE, getString(R.string.default_tmpl_label))
            putInt(KEY_LAST_SIG_DPI_INDEX, 0)
            putBoolean(KEY_LAST_SCAN_EFFECT, false)
            putBoolean(KEY_LAST_GRAYSCALE, false)
            putBoolean(KEY_LAST_INNER_PDF_READER, true)
            putString(KEY_LAST_LOADED_PROFILE, "normal")
            putString(KEY_THEME_MODE, "system")
            putBoolean(KEY_RESTORE_LOCK_UNTIL_RELOAD, false)
            putBoolean(KEY_FIRST_RUN_INITIALIZED, true)
        }
    }

    private fun saveSettings(type: String) {
        val prefs = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
        val fileNameEt: EditText = findViewById(R.id.file_name)

        prefs.edit {
            val rows = numberOfRows.text.toString()
            val columns = numberOfColumns.text.toString()
            val marginVal = margin.text.toString()
            val isPortrait = portrait.isChecked
            val drawFrame = drawFrameSwitch.isChecked
            val sigX = signaturePosX.text.toString()
            val sigY = signaturePosY.text.toString()
            val scale = scaleSwitch.isChecked
            val fileNameTemplate = fileNameEt.text?.toString() ?: ""
            val batchVal = batchPage.text.toString()
            val passwordOutChecked = passwordOutCb.isChecked

            when (type) {
                "normal" -> {
                    putString(KEY_NORMAL_ROWS, rows)
                    putString(KEY_NORMAL_COLUMNS, columns)
                    putString(KEY_NORMAL_MARGIN, marginVal)
                    putBoolean(KEY_NORMAL_ORIENTATION, isPortrait)
                    putBoolean(KEY_NORMAL_DRAW_FRAME, drawFrame)
                    putString(KEY_NORMAL_SIG_POS_X, sigX)
                    putString(KEY_NORMAL_SIG_POS_Y, sigY)
                    putBoolean(KEY_NORMAL_SCALE, scale)
                    putString(KEY_NORMAL_BATCH_PAGE, batchVal)
                    putBoolean(KEY_NORMAL_PASSWORD_OUT, passwordOutChecked)
                    putString(KEY_NORMAL_FILE_NAME_TEMPLATE, fileNameTemplate)
                }
                "label" -> {
                    putString(KEY_LABEL_ROWS, rows)
                    putString(KEY_LABEL_COLUMNS, columns)
                    putString(KEY_LABEL_MARGIN, marginVal)
                    putBoolean(KEY_LABEL_ORIENTATION, isPortrait)
                    putBoolean(KEY_LABEL_DRAW_FRAME, drawFrame)
                    putString(KEY_LABEL_SIG_POS_X, sigX)
                    putString(KEY_LABEL_SIG_POS_Y, sigY)
                    putBoolean(KEY_LABEL_SCALE, scale)
                    putString(KEY_LABEL_BATCH_PAGE, batchVal)
                    putBoolean(KEY_LABEL_PASSWORD_OUT, passwordOutChecked)
                    putString(KEY_LABEL_FILE_NAME_TEMPLATE, fileNameTemplate)
                }
            }
            putString(KEY_ROWS, rows)
            putString(KEY_COLUMNS, columns)
            putString(KEY_MARGIN, marginVal)
            putBoolean(KEY_ORIENTATION, isPortrait)
            putBoolean(KEY_DRAW_FRAME, drawFrame)
            putString(KEY_SIG_POS_X, sigX)
            putString(KEY_SIG_POS_Y, sigY)
            putBoolean(KEY_SCALE, scale)
            putString(KEY_BATCH_PAGE, batchVal)
            putBoolean(KEY_PASSWORD_OUT, passwordOutChecked)
            putString(KEY_FILE_NAME_TEMPLATE, fileNameTemplate)
        }
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
    }

    private fun saveCurrentSettings() {
        val prefs = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
        val fileNameEt: EditText = findViewById(R.id.file_name)
        prefs.edit {
            putString(KEY_ROWS, numberOfRows.text.toString())
            putString(KEY_COLUMNS, numberOfColumns.text.toString())
            putString(KEY_MARGIN, margin.text.toString())
            putBoolean(KEY_ORIENTATION, portrait.isChecked)
            putBoolean(KEY_DRAW_FRAME, drawFrameSwitch.isChecked)
            putString(KEY_SIG_POS_X, signaturePosX.text.toString())
            putString(KEY_SIG_POS_Y, signaturePosY.text.toString())
            putBoolean(KEY_SCALE, scaleSwitch.isChecked)
            putString(KEY_BATCH_PAGE, batchPage.text.toString())
            putBoolean(KEY_PASSWORD_OUT, passwordOutCb.isChecked)
            putString(KEY_FILE_NAME_TEMPLATE, fileNameEt.text?.toString() ?: "")
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)

        val rows = prefs.getString(KEY_ROWS, "1") ?: "1"
        val columns = prefs.getString(KEY_COLUMNS, "1") ?: "1"
        val marginVal = prefs.getString(KEY_MARGIN, "0") ?: "0"
        val isPortrait = prefs.getBoolean(KEY_ORIENTATION, true)
        val drawFrame = prefs.getBoolean(KEY_DRAW_FRAME, false)
        val sigX = prefs.getString(KEY_SIG_POS_X, DEFAULT_SIG_POS_X_MM.toString()) ?: DEFAULT_SIG_POS_X_MM.toString()
        val sigY = prefs.getString(KEY_SIG_POS_Y, DEFAULT_SIG_POS_Y_MM.toString()) ?: DEFAULT_SIG_POS_Y_MM.toString()
        val scale = prefs.getBoolean(KEY_SCALE, false)
        val batchVal = prefs.getString(KEY_BATCH_PAGE, "20") ?: "20"
        val passwordOutChecked = prefs.getBoolean(KEY_PASSWORD_OUT, false)

        numberOfRows.setText(rows)
        numberOfColumns.setText(columns)
        margin.setText(marginVal)
        portrait.isChecked = isPortrait
        landscape.isChecked = !isPortrait
        drawFrameSwitch.isChecked = drawFrame
        signaturePosX.setText(sigX)
        signaturePosY.setText(sigY)
        batchPage.setText(batchVal)
        passwordOutCb.isChecked = passwordOutChecked
        scaleSwitch.isChecked = scale


        val fileNameEt: EditText = findViewById(R.id.file_name)
        val defaultTemplate = getString(R.string.default_tmpl_normal)
        val tmpl = prefs.getString(KEY_FILE_NAME_TEMPLATE, defaultTemplate) ?: defaultTemplate
        fileNameEt.setText(tmpl)

        if (fileNameEt.getTag(R.id.file_name) != true) {
            fileNameEt.addTextChangedListener(SimpleTextWatcher {
                val sNow = fileNameEt.text?.toString()?.trim().orEmpty()
                validateIntField(
                    fileNameEt,
                    getString(R.string.file_name_optional),
                    isValid = sNow.isNotBlank()
                )
            })
            fileNameEt.setTag(R.id.file_name, true)
        }
        val sInit = fileNameEt.text?.toString()?.trim().orEmpty()
        validateIntField(
            fileNameEt,
            getString(R.string.file_name_optional),
            isValid = sInit.isNotBlank()
        )

        updateFileNameHintBasedOnRowsCols()
        validateInputs()
    }

    private fun loadSettings(type: String) {
        isLoadingSettingsProfile = true
        regenRequestedDuringProfileLoad = false

        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val prefs = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
                when (type) {
                    "normal" -> mapOf(
                        "nlRows" to (prefs.getString(KEY_NORMAL_ROWS, "1") ?: "1"),
                        "nlColumns" to (prefs.getString(KEY_NORMAL_COLUMNS, "1") ?: "1"),
                        "nlMargin" to (prefs.getString(KEY_NORMAL_MARGIN, "0") ?: "0"),
                        "nlOrientation" to prefs.getBoolean(KEY_NORMAL_ORIENTATION, true),
                        "nlDrawFrame" to prefs.getBoolean(KEY_NORMAL_DRAW_FRAME, false),
                        "nlSigPosX" to (prefs.getString(KEY_NORMAL_SIG_POS_X, DEFAULT_SIG_POS_X_MM.toString()) ?: DEFAULT_SIG_POS_X_MM.toString()),
                        "nlSigPosY" to (prefs.getString(KEY_NORMAL_SIG_POS_Y, DEFAULT_SIG_POS_Y_MM.toString()) ?: DEFAULT_SIG_POS_Y_MM.toString()),
                        "nlScale" to prefs.getBoolean(KEY_NORMAL_SCALE, false),
                        "nlBatchPage" to (prefs.getString(KEY_NORMAL_BATCH_PAGE, "20") ?: "20"),
                        "nlPasswordOut" to prefs.getBoolean(KEY_NORMAL_PASSWORD_OUT, false),
                        "nlFileNameTemplate" to (prefs.getString(KEY_NORMAL_FILE_NAME_TEMPLATE, getString(R.string.default_tmpl_normal)) ?: getString(R.string.default_tmpl_normal))
                    )
                    "label" -> mapOf(
                        "nlRows" to (prefs.getString(KEY_LABEL_ROWS, "2") ?: "2"),
                        "nlColumns" to (prefs.getString(KEY_LABEL_COLUMNS, "2") ?: "2"),
                        "nlMargin" to (prefs.getString(KEY_LABEL_MARGIN, "2") ?: "2"),
                        "nlOrientation" to prefs.getBoolean(KEY_LABEL_ORIENTATION, true),
                        "nlDrawFrame" to prefs.getBoolean(KEY_LABEL_DRAW_FRAME, false),
                        "nlSigPosX" to (prefs.getString(KEY_LABEL_SIG_POS_X, DEFAULT_SIG_POS_X_MM.toString()) ?: DEFAULT_SIG_POS_X_MM.toString()),
                        "nlSigPosY" to (prefs.getString(KEY_LABEL_SIG_POS_Y, DEFAULT_SIG_POS_Y_MM.toString()) ?: DEFAULT_SIG_POS_Y_MM.toString()),
                        "nlScale" to prefs.getBoolean(KEY_LABEL_SCALE, true),
                        "nlBatchPage" to (prefs.getString(KEY_LABEL_BATCH_PAGE, "20") ?: "20"),
                        "nlPasswordOut" to prefs.getBoolean(KEY_LABEL_PASSWORD_OUT, false),
                        "nlFileNameTemplate" to (prefs.getString(KEY_LABEL_FILE_NAME_TEMPLATE, getString(R.string.default_tmpl_label)) ?: getString(R.string.default_tmpl_label))
                    )
                    else -> emptyMap()
                }
            }

            numberOfRows.setText(data["nlRows"] as String)
            numberOfColumns.setText(data["nlColumns"] as String)
            margin.setText(data["nlMargin"] as String)
            val isPortrait = data["nlOrientation"] as Boolean
            portrait.isChecked = isPortrait
            landscape.isChecked = !isPortrait
            drawFrameSwitch.isChecked = data["nlDrawFrame"] as Boolean
            signaturePosX.setText(data["nlSigPosX"] as String)
            signaturePosY.setText(data["nlSigPosY"] as String)
            scaleSwitch.isChecked = data["nlScale"] as Boolean
            batchPage.setText(data["nlBatchPage"] as String)
            passwordOutCb.isChecked = data["nlPasswordOut"] as Boolean

            val fileNameEt: EditText = findViewById(R.id.file_name)
            fileNameEt.setText(data["nlFileNameTemplate"] as String)

            getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE).edit {
                putString(KEY_FILE_NAME_TEMPLATE, fileNameEt.text?.toString() ?: "")
                putString(KEY_LAST_LOADED_PROFILE, type)
            }

            updateFileNameHintBasedOnRowsCols()
            validateInputs()

            isLoadingSettingsProfile = false

            if (regenRequestedDuringProfileLoad && plpTempFile().exists()) {
                regenRequestedDuringProfileLoad = false
                regeneratePlpAsyncAndReloadThumbnails()
            }
        }
    }

    private fun updateFileNameHintBasedOnRowsCols() {
        val rowsStr = numberOfRows.text?.toString()?.trim().orEmpty()
        val colsStr = numberOfColumns.text?.toString()?.trim().orEmpty()

        val rows = rowsStr.toIntOrNull()
        val cols = colsStr.toIntOrNull()

        val isRowsEmpty = rowsStr.isEmpty()
        val isColsEmpty = colsStr.isEmpty()

        val isNormal =
            (isRowsEmpty && isColsEmpty) ||
                    (isRowsEmpty && cols == 1) ||
                    (rows == 1 && isColsEmpty) ||
                    (rows == 1 && cols == 1)

        val fileNameEt: EditText = findViewById(R.id.file_name)
        val hintRes = if (isNormal) R.string.default_tmpl_normal else R.string.default_tmpl_label
        fileNameEt.hint = getString(hintRes)
    }

    private fun validateInputs() {
        nbColumnsValid = validateIntField(numberOfColumns, 1, getString(R.string.enter_value_1_100))
        nbRowsValid    = validateIntField(numberOfRows,    1, getString(R.string.enter_value_1_100))
        marginValid    = validateIntField(margin,          0, getString(R.string.enter_value_0_100))
        batchValid     = validateIntField(batchPage,       1, getString(R.string.enter_value_batch_1_100))
        val isPortrait = portrait.isChecked
        val maxX = if (isPortrait) 210 else 297
        val maxY = if (isPortrait) 297 else 210
        sigXValid = checkUpToMax(signaturePosX.text.toString(), maxX)
        sigYValid = checkUpToMax(signaturePosY.text.toString(), maxY)

        validateIntFieldX(signaturePosX, portrait)
        validateIntFieldY(signaturePosY, portrait)

        updateFileNameHintBasedOnRowsCols()
        updateButtonsState()
    }

    private fun checkUpToMax(s: String, max: Int): Boolean {
        return try { val v = s.toInt(); v in 0..max } catch (_: Exception) { false }
    }

    private fun buildErrorTextFromDimen(et: EditText, msg: String, dimenRes: Int): CharSequence {
        val sizePx = et.resources.getDimensionPixelSize(dimenRes)
        return SpannableString(msg).apply {
            setSpan(AbsoluteSizeSpan(sizePx), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun validateIntField(
        et: EditText,
        min: Int,
        errorMsg: String,
        max: Int = 100
    ): Boolean {
        val s = et.text.toString()
        val ok = s.isNotEmpty() && (s.toIntOrNull()?.let { it in min..max } == true)

        et.error = if (ok) {
            null
        } else {
            val sizePx = et.resources.getDimensionPixelSize(R.dimen.dp_14)
            SpannableString(errorMsg).apply {
                setSpan(
                    AbsoluteSizeSpan(sizePx),
                    0, length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return ok
    }

    private fun validateIntField(
        et: EditText,
        errorMsg: String,
        isValid: Boolean
    ): Boolean {
        et.error = if (isValid) {
            null
        } else {
            val sizePx = et.resources.getDimensionPixelSize(R.dimen.dp_14)
            SpannableString(errorMsg).apply {
                setSpan(
                    AbsoluteSizeSpan(sizePx),
                    0, length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return isValid
    }

    private fun validateIntFieldX(
        et: EditText,
        portrait: MaterialRadioButton
    ): Boolean {
        val isPortrait = portrait.isChecked

        val emptyMsg = if (isPortrait) {
            et.context.getString(R.string.enter_value_0_209)
        } else {
            et.context.getString(R.string.enter_value_0_296)
        }

        val s = et.text?.toString()?.trim().orEmpty()
        val ok = s.isNotEmpty()

        et.error = if (ok) {
            null
        } else {
            buildErrorTextFromDimen(et, emptyMsg, R.dimen.dp_14)
        }

        return ok
    }

    private fun validateIntFieldY(
        et: EditText,
        portrait: MaterialRadioButton
    ): Boolean {
        val isPortrait = portrait.isChecked

        val emptyMsg = if (isPortrait) {
            et.context.getString(R.string.enter_value_0_296)
        } else {
            et.context.getString(R.string.enter_value_0_209)
        }

        val s = et.text?.toString()?.trim().orEmpty()
        val ok = s.isNotEmpty()

        et.error = if (ok) {
            null
        } else {
            buildErrorTextFromDimen(et, emptyMsg, R.dimen.dp_14)
        }

        return ok
    }

    private fun updateButtonsState() {
        val hasPages = viewModel.pageItems.isNotEmpty()
        val hasSelection = viewModel.pageItems.any { it.isSelected }

        listStartButton.isEnabled = hasPages
        listEndButton.isEnabled = hasPages

        emptyPage.isEnabled = hasPages

        addSignature.isEnabled = hasSelection && hasPages

        printPdf.isEnabled = hasPages
        savePdf.isEnabled = hasPages
        sendPdf.isEnabled  = hasPages

        val allSelect = findViewById<AppCompatCheckBox>(R.id.all_select)
        if (allSelect.getTag(R.id.all_select) != true) {
            allSelect.setOnClickListener {
                val shouldSelectAll = allSelect.isChecked
                if (shouldSelectAll) {
                    adapter.selectAll()
                } else {
                    adapter.clearAllSelections()
                }
                updateButtonsState()
            }
            allSelect.setTag(R.id.all_select, true)
        }
        allSelect.isEnabled = hasPages
        val allChecked = hasPages && viewModel.pageItems.all { it.isSelected }
        if (allSelect.isChecked != allChecked) {
            allSelect.isChecked = allChecked
        }

        val deleteBtn = findViewById<AppCompatImageButton>(R.id.delete_select)
        if (deleteBtn.getTag(R.id.delete_select) != true) {
            deleteBtn.setOnClickListener {
                deleteSelectedPlpGroups()
            }
            deleteBtn.setTag(R.id.delete_select, true)
        }
        deleteBtn.isEnabled   = hasPages && hasSelection
        deleteBtn.isActivated = hasSelection

        fastScroller?.setEnabledWhen(hasPages)

        val shouldNotify = (lastGlobalAllSelected == null || lastGlobalAllSelected != allChecked)
        lastGlobalAllSelected = allChecked
        if (shouldNotify) {

            val rv = findViewById<RecyclerView>(R.id.rv_thumbnails)
            val lm = rv.layoutManager as? LinearLayoutManager
            val first = lm?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            val last  = lm?.findLastVisibleItemPosition()  ?: RecyclerView.NO_POSITION
            if (first != RecyclerView.NO_POSITION && last >= first) {
                val count = last - first + 1
                if (rv.isComputingLayout) {
                    rv.post { adapter.notifyItemRangeChanged(first, count, "force") }
                } else {
                    adapter.notifyItemRangeChanged(first, count, "force")
                }
            }
        }
    }

    private fun resetThumbDocKey(reason: String) {
        (findViewById<RecyclerView>(R.id.rv_thumbnails).adapter as? ThumbnailAdapter)
            ?.resetForNewDocument("$reason|${System.nanoTime()}")
    }

    private fun showInProgress(
        message: String = getString(R.string.in_progress_my),
    ) {
        val resolvedMessage = message.ifBlank { getString(R.string.in_progress_my) }

        if (!::progressDialog.isInitialized || !progressDialog.isShowing) {
            val view = LayoutInflater.from(this).inflate(R.layout.progress_dialog, null)
            view.findViewById<MaterialTextView>(R.id.progress_bar_simple_text)?.text = resolvedMessage

            progressDialog = MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(false)
                .create().apply {
                    setCanceledOnTouchOutside(false)
                    show()
                }
        } else {
            progressDialog.findViewById<MaterialTextView>(R.id.progress_bar_simple_text)?.text = resolvedMessage
        }

        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }

    private fun hideInProgressIfShown() {
        if (::progressDialog.isInitialized && progressDialog.isShowing) {
            progressDialog.dismiss()
        }

        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun readIntOr(et: EditText, min: Int, def: Int): Int {
        val v = et.text?.toString()?.trim()?.toIntOrNull()
        return (v ?: def).coerceIn(min, MAX_INPUT)
    }

    private fun currentNupConfigFromInputs(): NupConfig {
        val rows     = readIntOr(numberOfRows,    min = 1, def = 1)
        val cols     = readIntOr(numberOfColumns, min = 1, def = 1)
        val marginMm = readIntOr(margin,          min = 0, def = 0)
        val isLand = landscape.isChecked
        val drawFrame = drawFrameSwitch.isChecked
        val scaleInd = scaleSwitch.isChecked
        return NupConfig(rows, cols, marginMm, isLand, drawFrame, scaleInd)
    }

    private fun nupConfigChanged(): Boolean {
        val cur = currentNupConfigFromInputs()
        val last = lastBuiltNupConfig
        return last == null || last != cur
    }

    private fun maybeRegeneratePlpOnSettingChange() {
        if (suppressNup) {
            return
        }

        if (plpTempFile().exists()) {
            if (isLoadingSettingsProfile) {
                regenRequestedDuringProfileLoad = true
                return
            }
            regeneratePlpAsyncAndReloadThumbnails()
        }
    }

    private fun setupRegenerateOnBlur(et: EditText) {

        et.setOnFocusChangeListener { _, hasFocus ->if (hasFocus) {
            et.setTag(et.id, et.text?.toString())
        } else {
            if (suppressNup || !plpTempFile().exists()) {
                return@setOnFocusChangeListener
            }

            val valueOnBlur = et.text?.toString()

            val valueOnFocus = et.getTag(et.id) as? String

            val normalizedValueOnFocus = when (et.id) {
                R.id.number_of_rows, R.id.number_of_columns -> if (valueOnFocus.isNullOrEmpty()) "1" else valueOnFocus
                R.id.margin -> if (valueOnFocus.isNullOrEmpty()) "0" else valueOnFocus
                else -> valueOnFocus
            }

            val normalizedValueOnBlur = when (et.id) {
                R.id.number_of_rows, R.id.number_of_columns -> if (valueOnBlur.isNullOrEmpty()) "1" else valueOnBlur
                R.id.margin -> if (valueOnBlur.isNullOrEmpty()) "0" else valueOnBlur
                else -> valueOnBlur
            }

            if (normalizedValueOnFocus != normalizedValueOnBlur) {
                if (nupConfigChanged()) {
                    regeneratePlpAsyncAndReloadThumbnails()
                }
            }
        }
        }
    }

    private fun launchSelectPdf() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/pdf",
                    "image/jpeg",
                    "image/jpg",
                    "image/png"
                )
            )
        }
        pdfPickerLauncher.launch(i)
    }

    private fun handlePdfUri(uri: Uri) {
        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        val selectedIdx = viewModel.pageItems
            .mapIndexedNotNull { idx, it -> if (it.isSelected) idx else null }

        val oldPer = run {
            val last = lastBuiltNupConfig ?: currentNupConfigFromInputs()
            (last.rows.coerceAtLeast(1)) * (last.cols.coerceAtLeast(1))
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mtLc = try { contentResolver.getType(uri)?.lowercase(Locale.ROOT) } catch (_: Exception) { null }
                val nameLcForType = try { extractFileName(uri).lowercase(Locale.ROOT) } catch (_: Exception) { "" }
                val isImage = isSupportedImageMime(mtLc) || isSupportedImageFileName(nameLcForType)

                val tempSrc = File(cacheDir, "${timeStamp}_${extractFileName(uri)}")
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(tempSrc).use { outputStream ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                } ?: throw IOException(getString(R.string.input_stream_is_null))

                try {

                    val oldPageCount = sourceTempPageCount()

                    val selectedSrcPages: Set<Int> = if (selectedIdx.isNotEmpty() && oldPageCount > 0) {
                        buildSet {
                            selectedIdx.forEach { g ->
                                val start = g * oldPer + 1
                                val end = min(oldPageCount, start + oldPer - 1)
                                for (p in start..end) add(p)
                            }
                        }
                    } else emptySet()

                    fun isBadPassword(t: Throwable): Boolean {
                        if (t is BadPasswordException) return true
                        val msg = t.message?.lowercase().orEmpty()
                        return msg.contains("bad user password") || msg.contains("bad password")
                    }

                    if (isImage) {
                        val imagePdf = createTempPdfFromImageFile(tempSrc)
                        try {
                            appendPdfToSourceTemp(imagePdf, null)
                        } finally {
                            runCatching { imagePdf.delete() }.onFailure {

                            }
                        }
                    } else {
                        var passwordBytes: ByteArray? = null
                        var attempt = 0
                        while (true) {
                            try {
                                appendPdfToSourceTemp(tempSrc, passwordBytes)
                                break
                            } catch (e: Exception) {
                                if (!isBadPassword(e)) throw e

                                withContext(Dispatchers.Main) {
                                    hideInProgressIfShown()
                                }
                                val entered: String? = withContext(Dispatchers.Main) {
                                    promptForPdfPassword(showError = (attempt > 0))
                                }
                                if (entered == null) {
                                    withContext(Dispatchers.Main) { }
                                    runCatching { tempSrc.delete() }
                                    return@launch
                                } else {
                                    passwordBytes = entered.toByteArray(Charsets.UTF_8)
                                    attempt++
                                    withContext(Dispatchers.Main) { showInProgress(getString(R.string.in_progress_my)) }
                                }
                            }
                        }
                    }

                    runCatching { tempSrc.delete() }

                    createTempPdfInternal()

                    withContext(Dispatchers.Main) {
                        resetThumbDocKey("handlePdfUri:$uri")

                        val per = perGroup()
                        val indicesToSelectAfter: Set<Int> =
                            if (selectedSrcPages.isNotEmpty())
                                selectedSrcPages.map { p -> (p - 1) / per }.toSet()
                            else emptySet()

                        val onReloadStarted: () -> Unit = {
                            hideInProgressIfShown()
                            unlockScreenOrientation()
                        }

                        reloadThumbnailsFromPlp(
                            targetIndex = null,
                            targetOffsetPx = 0,
                            restorePrevious = true,
                            indicesToSelect = indicesToSelectAfter,
                            releaseUiAtStart = false,
                            onStarted = onReloadStarted
                        )

                        updateButtonsState()
                    }
                } finally {
                    runCatching { tempSrc.delete() }.onFailure {

                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.error_loading_pdf_with_msg, e.message), Toast.LENGTH_LONG).show()
                    hideInProgressIfShown()
                    unlockScreenOrientation()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    unlockScreenOrientation()
                }
            }
        }
    }

    private fun isSupportedImageMime(mtLc: String?): Boolean {
        if (mtLc == null) return false
        return when (mtLc) {
            "image/jpeg", "image/jpg", "image/png" -> true
            else -> false
        }
    }

    private fun isSupportedImageFileName(nameLc: String): Boolean {
        return nameLc.endsWith(".jpg") ||
                nameLc.endsWith(".jpeg") ||
                nameLc.endsWith(".png")
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun createTempPdfFromImageFile(imageFile: File): File {
        val out = File(cacheDir, "${timeStamp}_from_image_${System.currentTimeMillis()}.pdf")

        val bounds0 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds0)
        val w0 = bounds0.outWidth
        val h0 = bounds0.outHeight

        if (w0 <= 0 || h0 <= 0) throw IOException(getString(R.string.image_decode_failed))

        if (w0 > MAX_IMG_W || h0 > MAX_IMG_H) {
            throw IOException(getString(R.string.large_image))
        }

        return try {
            if (imageFile.isLikelyPng()) {
                createPdfFromPngTiled(imageFile, out, w0, h0)
            } else {
                val imageData = ImageDataFactory.create(imageFile.absolutePath)
                val imgW = imageData.width.coerceAtLeast(1f)
                val imgH = imageData.height.coerceAtLeast(1f)
                val pageRect = Rectangle(imgW, imgH)

                PdfDocument(PdfWriter(out.absolutePath, writerProps())).use { destDoc ->
                    destDoc.setFlushUnusedObjects(true)
                    val pg = destDoc.addNewPage(PageSize(pageRect))
                    PdfCanvas(pg).addImageFittedIntoRectangle(imageData, pageRect, false)
                    pg.flush()
                }
                out
            }
        } catch (e: OutOfMemoryError) {
            throw e
        } catch (e: Throwable) {
            throw e
        }
    }

    private fun File.isLikelyPng(): Boolean {
        return try {
            FileInputStream(this).use { fis ->
                val header = ByteArray(8)
                if (fis.read(header) == 8) {
                    header[0] == 0x89.toByte() &&
                            header[1] == 0x50.toByte() && // P
                            header[2] == 0x4E.toByte() && // N
                            header[3] == 0x47.toByte() && // G
                            header[4] == 0x0D.toByte() &&
                            header[5] == 0x0A.toByte() &&
                            header[6] == 0x1A.toByte() &&
                            header[7] == 0x0A.toByte()
                } else false
            }
        } catch (_: Throwable) {
            name.lowercase().endsWith(".png")
        }
    }

    private object BitmapRegionDecoderCompat {

        @RequiresApi(VERSION_CODES.S)
        private object Api31Impl {
            @DoNotInline
            @JvmStatic
            fun newInstance(path: String): BitmapRegionDecoder {
                return BitmapRegionDecoder.newInstance(path)
            }
        }

        private object LegacyImpl {
            @Suppress("DEPRECATION")
            @JvmStatic
            fun newInstance(path: String): BitmapRegionDecoder {
                return BitmapRegionDecoder.newInstance(path, false)
            }
        }

        fun newInstance(file: File): BitmapRegionDecoder {
            val path = file.absolutePath
            return if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                Api31Impl.newInstance(path)
            } else {
                LegacyImpl.newInstance(path)
            }
        }
    }

    private fun newRegionDecoderCompat(pngFile: File): BitmapRegionDecoder {
        return BitmapRegionDecoderCompat.newInstance(pngFile)
    }

    private fun pickTileHeight(width: Int, bytesPerPixel: Int = 4): Int {
        val budgetBytes = 8 * 1024 * 1024
        val perRow = (width.toLong() * bytesPerPixel).coerceAtLeast(1L)
        return (budgetBytes / perRow).toInt().coerceIn(256, 2048)
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun createPdfFromPngTiled(
        pngFile: File,
        outFile: File,
        width: Int,
        height: Int
    ): File {
        val pageRect = Rectangle(width.toFloat(), height.toFloat())
        val tileHeight = pickTileHeight(width)

        val flattenBgColor = runCatching {
            ContextCompat.getColor(this@MainActivity, R.color.white)
        }.getOrElse { Color.WHITE }

        PdfDocument(PdfWriter(outFile.absolutePath, writerProps())).use { pdf ->
            pdf.setFlushUnusedObjects(true)
            val page = pdf.addNewPage(PageSize(pageRect))
            val canvas = PdfCanvas(page)

            val decoder = try {
                newRegionDecoderCompat(pngFile)
            } catch (e: Throwable) {
                throw IOException(getString(R.string.bitmap_region_decoder_init_failed), e)
            }

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bos = ByteArrayOutputStream(1 shl 20)
            try {
                var y = 0
                while (y < height) {
                    val h = min(tileHeight, height - y)
                    val srcRect = Rect(0, y, width, y + h)

                    val bmp: Bitmap = decoder.decodeRegion(srcRect, opts)
                        ?: throw IOException(getString(R.string.decode_region_returned_null_at, y))

                    var toRecycleFlattened: Bitmap? = null
                    try {
                        val toCompress: Bitmap = if (bmp.hasAlpha()) {
                            createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888).apply {
                                setHasAlpha(false)
                                Canvas(this).apply {
                                    drawColor(flattenBgColor)
                                    drawBitmap(bmp, 0f, 0f, null)
                                }
                            }.also { toRecycleFlattened = it }
                        } else {
                            bmp
                        }

                        bos.reset()
                        if (!toCompress.compress(Bitmap.CompressFormat.JPEG, 100, bos)) {
                            throw IOException(getString(R.string.jpeg_compress_failed_at, y))
                        }

                        val imgData = ImageDataFactory.create(bos.toByteArray())
                        val destY = (height - (y + h)).toFloat()
                        val destRect = Rectangle(0f, destY, width.toFloat(), h.toFloat())
                        canvas.addImageFittedIntoRectangle(imgData, destRect, false)
                    } finally {
                        toRecycleFlattened?.recycle()
                        bmp.recycle()
                    }

                    y += h
                }

                canvas.release()
                page.flush()
            } finally {
                try { bos.close() } catch (_: Throwable) {}
                decoder.recycle()
            }
        }

        return outFile
    }

    private fun setupPasswordInputForDialog(
        dialog: AlertDialog,
        dialogView: View,
        parent: ViewGroup,
        indexInParent: Int,
        lp: ViewGroup.LayoutParams,
        showError: Boolean,
        errorHintResId: Int? = null
    ): TextInputEditText {

        val listView = dialogView.findViewById<ListView>(R.id.listView)
        parent.removeView(listView)

        val asteriskTransformation = object : PasswordTransformationMethod() {
            private inner class AsteriskCharSequence(private val source: CharSequence) : CharSequence {
                override val length: Int get() = source.length
                override fun get(index: Int): Char = '*'
                override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
                    AsteriskCharSequence(source.subSequence(startIndex, endIndex))
                override fun toString(): String = "*".repeat(source.length)
            }
            override fun getTransformation(source: CharSequence?, view: View?): CharSequence {
                return if (source == null) "" else AsteriskCharSequence(source)
            }
        }

        class PasswordEditText(ctx: Context) : TextInputEditText(ctx) {
            private var passwordVisible: Boolean = false
            var iconTouchActive: Boolean = false

            private fun updateIcon() {
                runCatching {
                    val resId = if (passwordVisible) R.drawable.ic_visibility_on_24 else R.drawable.ic_visibility_off_24
                    val d = AppCompatResources.getDrawable(context, resId)?.mutate()
                    TextViewCompat.setCompoundDrawableTintList(this, null)
                    setCompoundDrawablesWithIntrinsicBounds(null, null, d, null)
                    refreshDrawableState()
                    invalidate()
                }.onFailure {
                    runCatching {
                        val fallback = AppCompatResources.getDrawable(context, R.drawable.ic_visibility_off_24)?.mutate()
                        // setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, fallback, null) // őőő
                        setCompoundDrawablesWithIntrinsicBounds(null, null, fallback, null)
                        refreshDrawableState()
                        invalidate()
                    }
                }
            }

            private inner class SavedSelection(private val edit: TextInputEditText) : Closeable {
                private val start = edit.selectionStart
                private val end   = edit.selectionEnd
                override fun close() {
                    try {
                        edit.setSelection(start, end.coerceAtLeast(start))
                    } catch (_: Throwable) { }
                }
            }

            fun toggleVisibility() {
                SavedSelection(this).use {
                    passwordVisible = !passwordVisible
                    transformationMethod = if (passwordVisible) null else asteriskTransformation
                }
                updateIcon()
                playSoundEffect(SoundEffectConstants.CLICK)
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
            }

            override fun performClick(): Boolean {
                super.performClick()
                return true
            }

            fun setInitialStateMasked() {
                transformationMethod = asteriskTransformation
                updateIcon()
                compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
                contentDescription = resources.getString(R.string.toggle_password_visibility)
                TextViewCompat.setCompoundDrawableTintList(this, null)
            }
        }

        val et = PasswordEditText(this).apply {
            layoutParams = lp
            hint = resources.getString(R.string.password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            setInitialStateMasked()
            runCatching { TextViewCompat.setTextAppearance(this, R.style.TextAppearance_PdfLabelPrinting_EditText_Normal) }
            runCatching {
                setTextColor(AppCompatResources.getColorStateList(context, R.color.edittext_text_color)!!)
            }.onFailure {
                runCatching { setTextColor(ContextCompat.getColor(context, R.color.edittext_text_color)) }
            }
        }

        et.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_DONE || isEnter) {
                dialog.findViewById<Button>(R.id.btn_ok)?.performClick()
                true
            } else {
                false
            }
        }

        et.setOnTouchListener { v, ev ->
            val endAbs = et.compoundDrawables[2]
            val endDrawable = endAbs ?: return@setOnTouchListener false

            val iconW = endDrawable.bounds.width()
            val x = ev.x

            fun isInsideIcon(): Boolean {
                val iconLeft  = et.width - et.paddingRight - iconW
                val iconRight = et.width - et.paddingRight
                return x >= iconLeft && x <= iconRight
            }

            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isInsideIcon()) {
                        et.iconTouchActive = true
                        et.cancelLongPress()
                        et.cancelPendingInputEvents()
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (et.iconTouchActive) {
                        if (!isInsideIcon()) et.iconTouchActive = false
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (et.iconTouchActive && isInsideIcon()) {
                        et.toggleVisibility()
                        v.performClick()
                        et.cancelPendingInputEvents()
                        et.iconTouchActive = false
                        return@setOnTouchListener true
                    }
                    et.iconTouchActive = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    et.iconTouchActive = false
                }
            }
            false
        }

        parent.addView(et, indexInParent)

        val originalHintColor = ContextCompat.getColor(this, R.color.password_hint_text_color)
        runCatching { et.setHintTextColor(originalHintColor) }

        if (errorHintResId != null || showError) {
            et.hint = resources.getString(errorHintResId ?: R.string.wrong_password)
            runCatching {
                et.setHintTextColor(ContextCompat.getColor(this, R.color.password_error_text_color))
            }
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!s.isNullOrEmpty()) {
                        et.hint = resources.getString(R.string.password_hint)
                        runCatching { et.setHintTextColor(originalHintColor) }
                        et.removeTextChangedListener(this)
                    }
                }
            }
            et.addTextChangedListener(watcher)
            dialog.setOnDismissListener {
                runCatching { et.removeTextChangedListener(watcher) }
            }
        }

        dialog.setOnShowListener {
            runCatching {
                et.requestFocus()
                dialog.window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                )
            }

            et.post {
                val imm2 = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                runCatching { imm2?.hideSoftInputFromWindow(et.windowToken, 0) }
            }
        }

        return et
    }

    private suspend fun promptForPdfPassword(showError: Boolean = false): String? =
        suspendCancellableCoroutine { cont ->
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_signature_chooser, null)
            val dialog = MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create()

            (dialogView as? ViewGroup)?.let { vg ->
                (vg.getChildAt(0) as? TextView)?.setText(R.string.enter_pdf_password)
            }

            val listView = dialogView.findViewById<ListView>(R.id.listView)

            val parent = listView.parent as ViewGroup
            val indexInParent = parent.indexOfChild(listView)
            val lp = listView.layoutParams

            val et = setupPasswordInputForDialog(
                dialog = dialog,
                dialogView = dialogView,
                parent = parent,
                indexInParent = indexInParent,
                lp = lp,
                showError = showError,
                errorHintResId = null
            )

            val btnNew    = dialogView.findViewById<Button>(R.id.btn_new)
            val btnRemove = dialogView.findViewById<Button>(R.id.btn_remove)
            val btnOk     = dialogView.findViewById<Button>(R.id.btn_ok)
            val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

            btnNew.visibility = View.GONE
            btnRemove.visibility = View.GONE

            btnOk.setOnClickListener {
                val pwd = et.text?.toString()?.trim().orEmpty()
                dialog.dismiss()
                if (!cont.isCompleted) cont.resume(pwd)
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
                if (!cont.isCompleted) cont.resume(null)
            }

            dialog.setOnCancelListener {
                if (!cont.isCompleted) cont.resume(null)
            }

            cont.invokeOnCancellation {
                if (dialog.isShowing) dialog.dismiss()
            }

            dialog.show()
        }

    private fun extractFileName(uri: Uri): String {
        var result: String? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) result = cursor.getString(idx)
                }
            }
        } catch (_: Exception) { }
        return result ?: uri.lastPathSegment ?: getString(R.string.unknown_pdf)
    }

    private fun writerProps(): WriterProperties =
        WriterProperties().setFullCompressionMode(true).setCompressionLevel(CompressionConstants.BEST_SPEED)

    private fun rdr(path: String, password: ByteArray? = null): PdfReader =
        PdfReader(
            path,
            ReaderProperties().apply {
                if (password != null) {
                    setPassword(password)
                }
            }
        ).apply {
            setMemorySavingMode(true)
            isCloseStream = true
            setUnethicalReading(true)
        }

    private fun batchValueOrDefault1(): Int {
        val sNow = batchPage.text?.toString()?.trim().orEmpty()
        val v = sNow.toIntOrNull()
        return if (v != null && v > 0) v else 1
    }

    private fun appendPdfToSourceTemp(srcPdf: File, passwordBytes: ByteArray? = null) {

        val outWork = sourceWorkFile()
        if (outWork.exists()) outWork.delete()

        val pageBatch = batchValueOrDefault1()

        PdfDocument(
            PdfWriter(outWork.absolutePath, writerProps())
        ).use { dest ->
            dest.setFlushUnusedObjects(true)

            fun totalPagesOf(path: String, pw: ByteArray?): Int =
                PdfDocument(rdr(path, pw)).use { it.numberOfPages }

            fun copyInBatches(inputPath: String, pw: ByteArray?) {
                val total = totalPagesOf(inputPath, pw)
                var from = 1
                while (from <= total) {
                    val to = min(from + pageBatch - 1, total)

                    PdfDocument(rdr(inputPath, pw)).use { src ->
                        src.copyPagesTo(from, to, dest)
                        runCatching { dest.flushCopiedObjects(src) }
                    }

                    val added = to - from + 1
                    for (i in 0 until added) {
                        val p = dest.numberOfPages - i
                        if (p >= 1) runCatching { dest.getPage(p).flush() }
                    }
                    from = to + 1
                }
            }

            val sourceTemp = sourceTempFile()
            if (sourceTemp.exists()) copyInBatches(sourceTemp.absolutePath, null)
            copyInBatches(srcPdf.absolutePath, passwordBytes)
        }

        val target = sourceTempFile()
        if (target.exists()) target.delete()
        outWork.renameTo(target)
    }

    private fun addEmptyPageToSource() {
        val count = sourceTempPageCount()
        if (count <= 0) {
            Toast.makeText(this, getString(R.string.select_a_page), Toast.LENGTH_SHORT).show()
            return
        }

        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        val selectedIdx = viewModel.pageItems
            .mapIndexedNotNull { idx, it -> if (it.isSelected) idx else null }

        val oldPer = run {
            val last = lastBuiltNupConfig ?: currentNupConfigFromInputs()
            (last.rows.coerceAtLeast(1)) * (last.cols.coerceAtLeast(1))
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val source = sourceTempFile()

                val firstPageSize: PageSize = PdfDocument(rdr(source.absolutePath)).use { src ->
                    val p = src.getPage(1).pageSize
                    PageSize(p.width, p.height)
                }

                val outWork = sourceWorkFile()
                if (outWork.exists()) outWork.delete()

                val oldPageCount = sourceTempPageCount()

                val selectedSrcPages: Set<Int> = if (selectedIdx.isNotEmpty() && oldPageCount > 0) {
                    buildSet {
                        selectedIdx.forEach { g ->
                            val start = g * oldPer + 1
                            val end = min(oldPageCount, start + oldPer - 1)
                            for (p in start..end) add(p)
                        }
                    }
                } else emptySet()

                PdfDocument(PdfWriter(outWork.absolutePath, writerProps())).use { dest ->
                    dest.setFlushUnusedObjects(true)

                    val pageBatch = batchValueOrDefault1()

                    fun copyRange1BasedPaged(from1: Int, to1: Int) {
                        var from = from1
                        while (from <= to1) {
                            val to = min(from + pageBatch - 1, to1)

                            PdfDocument(rdr(source.absolutePath)).use { src ->
                                src.copyPagesTo(from, to, dest)
                                runCatching { dest.flushCopiedObjects(src) }
                            }

                            val added = to - from + 1
                            for (i in 0 until added) {
                                val p = dest.numberOfPages - i
                                if (p >= 1) runCatching { dest.getPage(p).flush() }
                            }

                            from = to + 1
                        }
                    }

                    if (oldPageCount > 0) copyRange1BasedPaged(1, oldPageCount)

                    val newPage = dest.addNewPage(firstPageSize)
                    runCatching { newPage.flush() }
                }

                val target = sourceTempFile()
                if (target.exists()) target.delete()
                outWork.renameTo(target)

                createTempPdfInternal()

                withContext(Dispatchers.Main) {
                    resetThumbDocKey("addEmptyPageToSource")

                    val per = perGroup()
                    val indicesToSelectAfter: Set<Int> =
                        if (selectedSrcPages.isNotEmpty())
                            selectedSrcPages.map { p -> (p - 1) / per }.toSet()
                        else emptySet()

                    val onReloadStarted: () -> Unit = {
                        hideInProgressIfShown()
                        unlockScreenOrientation()
                    }

                    reloadThumbnailsFromPlp(
                        targetIndex = null,
                        targetOffsetPx = 0,
                        restorePrevious = true,
                        indicesToSelect = indicesToSelectAfter,
                        releaseUiAtStart = false,
                        onStarted = onReloadStarted
                    )

                    updateButtonsState()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_add_empty_page_with_msg, e.message), Toast.LENGTH_LONG).show()
                    hideInProgressIfShown()
                    unlockScreenOrientation()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    unlockScreenOrientation()
                }
            }
        }
    }

    private suspend fun createTempPdfInternal() = withContext(Dispatchers.IO) {
        val final = plpTempFile()
        val work  = File(cacheDir, "${PLP_TEMP_NAME}.part")

        if (work.exists()) work.delete()
        createNupPdf(work)

        if (final.exists()) final.delete()
        work.renameTo(final)

        lastBuiltNupConfig = currentNupConfigFromInputs()
    }

    private fun regeneratePlpAsyncAndReloadThumbnails() {
        if (suppressNup) return

        if (isPlpRebuilding) return
        isPlpRebuilding = true

        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        val selectedIdx = viewModel.pageItems
            .mapIndexedNotNull { idx, it -> if (it.isSelected) idx else null }

        val totalSrcPages = sourceTempPageCount()

        val oldPer = run {
            val last = lastBuiltNupConfig ?: currentNupConfigFromInputs()
            (last.rows.coerceAtLeast(1)) * (last.cols.coerceAtLeast(1))
        }

        val newPer = run {
            val cur = currentNupConfigFromInputs()
            (cur.rows.coerceAtLeast(1)) * (cur.cols.coerceAtLeast(1))
        }

        val applySelection = !clearSelectionOnNextRebuild

        val indicesToSelectAfter: Set<Int> =
            if (applySelection && selectedIdx.isNotEmpty() && totalSrcPages > 0) {
                val selectedPages = mutableSetOf<Int>()
                selectedIdx.forEach { g ->
                    val start = g * oldPer + 1
                    val end = min(totalSrcPages, start + oldPer - 1)
                    for (p in start..end) selectedPages.add(p)
                }
                selectedPages.map { p -> (p - 1) / newPer }.toSet()
            } else emptySet()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                createTempPdfInternal()
                withContext(Dispatchers.Main) {
                    resetThumbDocKey("regeneratePlp")

                    val onReloadStarted: () -> Unit = {
                        hideInProgressIfShown()
                        unlockScreenOrientation()
                    }

                    reloadThumbnailsFromPlp(
                        targetIndex = null,
                        targetOffsetPx = 0,
                        restorePrevious = true,
                        indicesToSelect = indicesToSelectAfter,
                        releaseUiAtStart = false,
                        onStarted = onReloadStarted
                    )

                    updateButtonsState()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_build_pages_with_msg, e.message), Toast.LENGTH_LONG).show()
                    hideInProgressIfShown()
                    unlockScreenOrientation()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isPlpRebuilding = false
                    clearSelectionOnNextRebuild = false
                    unlockScreenOrientation()
                }
            }
        }
    }

    private fun clearThumbnails() {
        val oldCount = viewModel.pageItems.size
        viewModel.pageItems.forEach { it.thumbnail?.recycle() }
        if (oldCount > 0) {
            viewModel.pageItems.clear()
            adapter.notifyItemRangeRemoved(0, oldCount)
        } else {
            viewModel.pageItems.clear()
        }
    }

    private fun reloadThumbnailsFromPlp(
        targetIndex: Int? = null,
        targetOffsetPx: Int = 0,
        restorePrevious: Boolean = true,
        indicesToSelect: Set<Int>? = null,
        releaseUiAtStart: Boolean = false,
        onStarted: (() -> Unit)? = null
    ) {
        if (releaseUiAtStart) {
            hideInProgressIfShown()
        }

        val lm = binding.rvThumbnails.layoutManager as LinearLayoutManager
        val firstVisiblePosition = lm.findFirstVisibleItemPosition()
        val offset = lm.findViewByPosition(firstVisiblePosition)?.top ?: 0

        clearThumbnails()

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    val plp = plpTempFile()
                    val srcFile = sourceTempFile()
                    if (!plp.exists() || !srcFile.exists()) return@withContext emptyList()

                    val per = perGroup()
                    val totalSrcPages = sourceTempPageCount()
                    if (totalSrcPages <= 0) return@withContext emptyList()

                    val plpPages = (totalSrcPages + per - 1) / per
                    val list = ArrayList<PageItem>(plpPages)

                    val cfg = mappingConfig()
                    val rows = max(1, min(100, cfg.rows))
                    val cols = max(1, min(100, cfg.cols))
                    val isLandscape = cfg.isLandscape
                    val basePs = if (isLandscape) PageSize.A4.rotate()
                    else PageSize.A4
                    val isSingleUp = (rows * cols) == 1

                    val psForPlpPageFallback = if (isSingleUp) {
                        if (!isLandscape) PageSize.A4 else basePs
                    } else {
                        basePs
                    }
                    val fallbackW = psForPlpPageFallback.width
                    val fallbackH = psForPlpPageFallback.height

                    val skipReadingAnyPdfSize = (rows > 1 || cols > 1)

                    if (skipReadingAnyPdfSize) {
                        for (g in 0 until plpPages) {
                            val pageNumberText = if (per == 1) {
                                "${g + 1}/$totalSrcPages"
                            } else {
                                val totalGroups = (totalSrcPages + per - 1) / per
                                "${g + 1}/$totalGroups"
                            }

                            list.add(
                                PageItem(
                                    thumbnail = null,
                                    filePath = plp.absolutePath,
                                    pageIndex = g,
                                    widthPts = fallbackW,
                                    heightPts = fallbackH,
                                    pageNumberText = pageNumberText
                                )
                            )
                        }
                    } else {
                        ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                for (g in 0 until plpPages) {
                                    val pageNumberText = if (per == 1) {
                                        "${g + 1}/$totalSrcPages"
                                    } else {
                                        val totalGroups = (totalSrcPages + per - 1) / per
                                        "${g + 1}/$totalGroups"
                                    }

                                    val firstSrcPageZeroBased = g * per

                                    var widthPtsForLabel = fallbackW
                                    var heightPtsForLabel = fallbackH

                                    if (firstSrcPageZeroBased in 0 until renderer.pageCount) {
                                        runCatching {
                                            renderer.openPage(firstSrcPageZeroBased).use { pg ->
                                                widthPtsForLabel = pg.width.toFloat()
                                                heightPtsForLabel = pg.height.toFloat()
                                            }
                                        }.onFailure {

                                        }
                                    }

                                    list.add(
                                        PageItem(
                                            thumbnail = null,
                                            filePath = plp.absolutePath,
                                            pageIndex = g,
                                            widthPts = widthPtsForLabel,
                                            heightPts = heightPtsForLabel,
                                            pageNumberText = pageNumberText
                                        )
                                    )
                                }
                            }
                        }
                    }

                    list
                } catch (_: Exception) {
                    emptyList()
                }
            }

            if (items.isEmpty()) {
                updateButtonsState()
                onStarted?.invoke()
                return@launch
            }

            viewModel.pageItems.addAll(items)
            adapter.notifyItemRangeInserted(0, items.size)

            onStarted?.invoke()

            binding.rvThumbnails.post {
                val lm2 = binding.rvThumbnails.layoutManager as? LinearLayoutManager

                when {
                    targetIndex != null -> lm2?.scrollToPositionWithOffset(targetIndex, targetOffsetPx)
                    restorePrevious && firstVisiblePosition != RecyclerView.NO_POSITION ->
                        lm2?.scrollToPositionWithOffset(firstVisiblePosition, offset)
                }

                val f = lm2?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                val l = lm2?.findLastVisibleItemPosition() ?: RecyclerView.NO_POSITION
                if (f != RecyclerView.NO_POSITION && l >= f) {
                    adapter.notifyItemRangeChanged(f, l - f + 1, "force")
                    val start = (f - 4).coerceAtLeast(0)
                    val end   = (l + 4).coerceAtMost(adapter.itemCount - 1)
                    adapter.prefetchAroundVisible(this@MainActivity, start..end)
                }

                indicesToSelect?.let { set ->
                    val max = viewModel.pageItems.size
                    set.forEach { i ->
                        if (i in 0 until max) {
                            viewModel.pageItems[i].isSelected = true
                            adapter.notifyItemChanged(i, "force")
                        }
                    }
                    updateButtonsState()
                }
            }

            updateButtonsState()
        }
    }

    private fun mappingConfig(): NupConfig {
        return lastBuiltNupConfig ?: currentNupConfigFromInputs()
    }

    private fun perGroup(): Int {
        val cfg = mappingConfig()
        val r = max(1, min(100, cfg.rows))
        val c = max(1, min(100, cfg.cols))
        return r * c
    }

    private fun persistPlpOrderToSourceTemp() {
        if (viewModel.pageItems.isEmpty()) return

        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        val selectedIdx = viewModel.pageItems
            .mapIndexedNotNull { idx, it -> if (it.isSelected) idx else null }

        val ordered = viewModel.pageItems.toList()
        val per = perGroup()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val srcFile = sourceTempFile()
                if (!srcFile.exists()) {
                    withContext(Dispatchers.Main) {
                        hideInProgressIfShown()
                    }
                    return@launch
                }

                val totalSrcPages = sourceTempPageCount()

                val outWork = sourceWorkFile()
                if (outWork.exists()) outWork.delete()

                PdfDocument(PdfWriter(outWork.absolutePath, writerProps())).use { dest ->
                    dest.setFlushUnusedObjects(true)

                    val pageBatch = batchValueOrDefault1()

                    fun copyRange1BasedPaged(from1: Int, to1: Int) {
                        var from = from1
                        while (from <= to1) {
                            val to = min(from + pageBatch - 1, to1)
                            PdfDocument(rdr(srcFile.absolutePath)).use { src ->
                                src.copyPagesTo(from, to, dest)
                                runCatching { dest.flushCopiedObjects(src) }
                            }
                            val added = to - from + 1
                            for (i in 0 until added) {
                                val p = dest.numberOfPages - i
                                if (p >= 1) runCatching { dest.getPage(p).flush() }
                            }
                            from = to + 1
                        }
                    }

                    var runStartGroup: Int? = null
                    var runEndGroup: Int? = null

                    fun flushRunIfAny() {
                        val rs = runStartGroup
                        val re = runEndGroup
                        if (rs != null && re != null) {
                            val firstStart0 = rs * per
                            val lastEnd0 = min(totalSrcPages - 1, re * per + (per - 1))
                            if (firstStart0 <= lastEnd0) {
                                val from1 = firstStart0 + 1
                                val to1   = lastEnd0 + 1
                                copyRange1BasedPaged(from1, to1)
                            }
                        }
                        runStartGroup = null
                        runEndGroup = null
                    }

                    for (item in ordered) {
                        val gi = item.pageIndex
                        if (runStartGroup == null) {
                            runStartGroup = gi
                            runEndGroup = gi
                        } else if (gi == (runEndGroup!! + 1)) {
                            runEndGroup = gi
                        } else {
                            flushRunIfAny()
                            runStartGroup = gi
                            runEndGroup = gi
                        }
                    }
                    flushRunIfAny()
                }

                if (srcFile.exists()) srcFile.delete()
                outWork.renameTo(srcFile)

                createTempPdfInternal()

                withContext(Dispatchers.Main) {
                    resetThumbDocKey("reorder")

                    val onReloadStarted: () -> Unit = {
                        hideInProgressIfShown()
                        unlockScreenOrientation()
                    }

                    reloadThumbnailsFromPlp(
                        targetIndex = null,
                        targetOffsetPx = 0,
                        restorePrevious = true,
                        indicesToSelect = selectedIdx.toSet(),
                        releaseUiAtStart = false,
                        onStarted = onReloadStarted
                    )

                    updateButtonsState()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.reorder_failed_with_msg, e.message), Toast.LENGTH_LONG).show()
                    hideInProgressIfShown()
                    unlockScreenOrientation()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    unlockScreenOrientation()
                }
            }
        }
    }

    private fun deletePlpPageGroupAndMirrorSource(plpIndex: Int) {
        val total = sourceTempPageCount()
        if (total <= 0) return
        val per = perGroup()

        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        val selectedIdxBefore = viewModel.pageItems
            .mapIndexedNotNull { idx, it -> if (it.isSelected) idx else null }

        val toReselectAfter: List<Int> = selectedIdxBefore
            .filter { it != plpIndex }
            .map { if (it > plpIndex) it - 1 else it }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val source = sourceTempFile()

                val groupStart = plpIndex * per
                val groupEnd = min(total - 1, groupStart + per - 1)
                val toSkipCount = if (groupStart <= groupEnd) (groupEnd - groupStart + 1) else 0
                val remaining = total - toSkipCount

                if (remaining <= 0) {
                    if (source.exists()) source.delete()
                    plpTempFile().delete()
                    withContext(Dispatchers.Main) {
                        clearThumbnails()
                        updateButtonsState()
                        hideInProgressIfShown()
                    }
                    return@launch
                }

                val outWork = sourceWorkFile()
                if (outWork.exists()) outWork.delete()

                val pageBatch = batchValueOrDefault1()

                fun copyRange1BasedPaged(from1: Int, to1: Int, dest: PdfDocument, srcPath: String) {
                    var from = from1
                    while (from <= to1) {
                        val to = min(from + pageBatch - 1, to1)
                        PdfDocument(rdr(srcPath)).use { src ->
                            src.copyPagesTo(from, to, dest)
                            runCatching { dest.flushCopiedObjects(src) }
                        }
                        val added = to - from + 1
                        for (i in 0 until added) {
                            val p = dest.numberOfPages - i
                            if (p >= 1) runCatching { dest.getPage(p).flush() }
                        }
                        from = to + 1
                    }
                }

                PdfDocument(PdfWriter(outWork.absolutePath, writerProps())).use { dest ->
                    dest.setFlushUnusedObjects(true)

                    var runStart: Int? = null
                    var currentRunEnd = -1

                    fun flushRun(rs0: Int, re0: Int) {
                        if (rs0 <= re0) {
                            val from1 = rs0 + 1
                            val to1   = re0 + 1
                            copyRange1BasedPaged(from1, to1, dest, source.absolutePath)
                        }
                    }

                    for (pZero in 0 until total) {
                        if (pZero in groupStart..groupEnd) {
                            runStart?.let { flushRun(it, currentRunEnd) }
                            runStart = null
                            continue
                        }
                        if (runStart == null) {
                            runStart = pZero
                            currentRunEnd = pZero
                        } else if (pZero == currentRunEnd + 1) {
                            currentRunEnd = pZero
                        } else {
                            flushRun(runStart, currentRunEnd)
                            runStart = pZero
                            currentRunEnd = pZero
                        }
                    }
                    runStart?.let { flushRun(it, currentRunEnd) }
                }

                if (source.exists()) source.delete()
                outWork.renameTo(source)

                createTempPdfInternal()

                withContext(Dispatchers.Main) {
                    resetThumbDocKey("deleteGroup:$plpIndex")

                    val onReloadStarted: () -> Unit = {
                        hideInProgressIfShown()
                        unlockScreenOrientation()
                    }

                    reloadThumbnailsFromPlp(
                        targetIndex = null,
                        targetOffsetPx = 0,
                        restorePrevious = true,
                        indicesToSelect = toReselectAfter.toSet(),
                        releaseUiAtStart = false,
                        onStarted = onReloadStarted
                    )

                    updateButtonsState()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_delete_page_group_with_msg, e.message), Toast.LENGTH_LONG).show()
                    hideInProgressIfShown()
                    unlockScreenOrientation()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    unlockScreenOrientation()
                }
            }
        }
    }

    private fun deleteSelectedPlpGroups() {
        val total = sourceTempPageCount()
        if (total <= 0) return

        val selectedPlp = viewModel.pageItems
            .mapIndexedNotNull { idx, it -> if (it.isSelected) idx else null }
            .sorted()

        if (selectedPlp.isEmpty()) return

        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val per = perGroup()
                val source = sourceTempFile()

                val toSkip = HashSet<Int>().apply {
                    for (pi in selectedPlp) {
                        val start = pi * per
                        val end = min(total - 1, start + per - 1)
                        for (i in start..end) add(i)
                    }
                }

                val remaining = total - toSkip.size
                if (remaining <= 0) {
                    if (source.exists()) source.delete()
                    plpTempFile().delete()
                    withContext(Dispatchers.Main) {
                        clearThumbnails()
                        updateButtonsState()
                        hideInProgressIfShown()
                    }
                    return@launch
                }

                val outWork = sourceWorkFile()
                if (outWork.exists()) outWork.delete()

                val pageBatch = batchValueOrDefault1()

                fun copyRange1BasedPaged(from1: Int, to1: Int, dest: PdfDocument, srcPath: String) {
                    var from = from1
                    while (from <= to1) {
                        val to = min(from + pageBatch - 1, to1)
                        PdfDocument(rdr(srcPath)).use { src ->
                            src.copyPagesTo(from, to, dest)
                            runCatching { dest.flushCopiedObjects(src) }
                        }
                        val added = to - from + 1
                        for (i in 0 until added) {
                            val p = dest.numberOfPages - i
                            if (p >= 1) runCatching { dest.getPage(p).flush() }
                        }
                        from = to + 1
                    }
                }

                PdfDocument(PdfWriter(outWork.absolutePath, writerProps())).use { dest ->
                    dest.setFlushUnusedObjects(true)

                    var runStart: Int? = null
                    var currentRunEnd = -1

                    fun flushRun(rs0: Int, re0: Int) {
                        if (rs0 <= re0) {
                            val from1 = rs0 + 1
                            val to1   = re0 + 1
                            copyRange1BasedPaged(from1, to1, dest, source.absolutePath)
                        }
                    }

                    for (pZero in 0 until total) {
                        if (pZero in toSkip) {
                            runStart?.let { flushRun(it, currentRunEnd) }
                            runStart = null
                            continue
                        }
                        if (runStart == null) {
                            runStart = pZero
                            currentRunEnd = pZero
                        } else if (pZero == currentRunEnd + 1) {
                            currentRunEnd = pZero
                        } else {
                            flushRun(runStart, currentRunEnd)
                            runStart = pZero
                            currentRunEnd = pZero
                        }
                    }
                    runStart?.let { flushRun(it, currentRunEnd) }
                }

                if (source.exists()) source.delete()
                outWork.renameTo(source)

                createTempPdfInternal()

                withContext(Dispatchers.Main) {
                    resetThumbDocKey("deleteSelected")

                    for (i in selectedPlp.asReversed()) {
                        if (i in 0 until viewModel.pageItems.size) {
                            val removed = viewModel.pageItems.removeAt(i)
                            removed.thumbnail = null
                            adapter.notifyItemRemoved(i)
                        }
                    }

                    val onReloadStarted: () -> Unit = {
                        hideInProgressIfShown()
                        unlockScreenOrientation()
                    }

                    reloadThumbnailsFromPlp(
                        releaseUiAtStart = false,
                        onStarted = onReloadStarted
                    )

                    updateButtonsState()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_delete_selected_groups_with_msg, e.message), Toast.LENGTH_LONG).show()
                    hideInProgressIfShown()
                    unlockScreenOrientation()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    unlockScreenOrientation()
                }
            }
        }
    }

    private fun exportNamedOutputPdf(plp: File): File {
        cleanupOldExports()

        val baseName = buildOutputFileName().ifBlank { defaultDateForFileName() }
        val safe = sanitizeFileNameBase(baseName)
        val out = File(cacheDir, "$safe.pdf")
        return try {
            FileInputStream(plp).use { inp ->
                FileOutputStream(out).use { outP -> inp.copyTo(outP) }
            }
            out
        } catch (_: Exception) {
            plp
        }
    }

    private fun cleanupOldExports(keepRecent: Int = KEEP_RECENT_EXPORTS) {
        val protected = setOf(PLP_TEMP_NAME, SOURCE_TEMP_NAME, SOURCE_TEMP_WORK)

        val pdfs = cacheDir
            .listFiles { f -> f.isFile && f.extension.equals("pdf", true) && f.name !in protected }
            ?.toList()
            .orEmpty()

        val sorted = pdfs.sortedByDescending { it.lastModified() }

        if (sorted.size > keepRecent) {
            sorted.drop(keepRecent).forEach { it.delete() }
        }
    }

    override fun onStop() {
        try {
            val u = lastGrantedViewUri
            if (u != null) {
                revokeUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) { }
        lastGrantedViewUri = null
        lastGrantedPkg = null

        super.onStop()
    }

    private fun isAndroidxPdfAvailable(): Boolean =
        try {
            Class.forName("androidx.pdf.viewer.fragment.PdfViewerFragment")
            true
        } catch (_: Throwable) { false }

    private fun isPdfViewerDeclared(): Boolean =
        try {
            packageManager.getActivityInfo(
                ComponentName(this, PdfViewer::class.java),
                0
            )
            true
        } catch (_: Exception) { false }

    private fun openPdfWithDriveOrOther() {
        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        lifecycleScope.launch {
            try {
                val plp = withContext(Dispatchers.IO) {
                    val f = plpTempFile()
                    if (!f.exists()) {
                        throw IllegalStateException(getString(R.string.temporary_pdf_not_found))
                    }
                    f
                }
                val named = withContext(Dispatchers.IO) { exportNamedOutputPdf(plp) }

                val uri = withContext(Dispatchers.IO) {
                    FileProvider.getUriForFile(this@MainActivity, "${packageName}.provider", named)
                }

                hideInProgressIfShown()
                unlockScreenOrientation()

                val shouldUseInternal: Boolean =
                    if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                        runCatching {
                            val prefs = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
                            prefs.getBoolean(KEY_LAST_INNER_PDF_READER, true)
                        }.getOrDefault(true)
                    } else {
                        false
                    }

                if (shouldUseInternal && isAndroidxPdfAvailable() && isPdfViewerDeclared()) {
                    try {
                        val i = Intent(this@MainActivity, PdfViewer::class.java).apply {
                            putExtra(PdfViewer.EXTRA_URI, uri.toString())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(i)
                        return@launch
                    } catch (_: Exception) {
                    }
                }

                val baseViewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val pm = packageManager
                val chooserTitle = runCatching { getString(R.string.open_with) }.getOrElse { "Open with" }

                if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                    val resolves = pm.queryIntentActivities(baseViewIntent, 0)
                    val hasOther = resolves.any { it.activityInfo.packageName != packageName }
                    if (!hasOther) {
                        Toast.makeText(this@MainActivity, getString(R.string.no_app_can_open_pdf), Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val ownComponents = resolves
                        .filter { it.activityInfo.packageName == packageName }
                        .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
                        .toTypedArray()

                    val chooser = Intent.createChooser(baseViewIntent, chooserTitle).apply {
                        if (ownComponents.isNotEmpty()) {
                            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownComponents)
                        }
                    }

                    val old = StrictMode.allowThreadDiskReads()
                    try {
                        startActivity(chooser)
                    } finally {
                        StrictMode.setThreadPolicy(old)
                    }
                } else {
                    val resolves = pm.queryIntentActivities(baseViewIntent, 0)
                    val others = resolves.filter { it.activityInfo.packageName != packageName }
                    if (others.isEmpty()) {
                        Toast.makeText(this@MainActivity, getString(R.string.no_app_can_open_pdf), Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val ownComponents = resolves
                        .filter { it.activityInfo.packageName == packageName }
                        .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }

                    ownComponents.forEach {
                        pm.setComponentEnabledSetting(
                            it,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }

                    val old = StrictMode.allowThreadDiskReads()
                    try {
                        startActivity(Intent.createChooser(baseViewIntent, chooserTitle))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, getString(R.string.no_app_can_open_pdf), Toast.LENGTH_LONG).show()
                    } finally {
                        StrictMode.setThreadPolicy(old)
                        Handler(Looper.getMainLooper()).postDelayed({
                            ownComponents.forEach { comp ->
                                pm.setComponentEnabledSetting(
                                    comp,
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                    PackageManager.DONT_KILL_APP
                                )
                            }
                        }, 500)
                    }
                }
            } catch (e: Exception) {
                hideInProgressIfShown()
                unlockScreenOrientation()
                Toast.makeText(this@MainActivity, getString(R.string.failed_to_open_pdf_with_msg, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun printNupPdf() {
        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        lifecycleScope.launch {
            try {
                val (namedPath, namedName) = withContext(Dispatchers.IO) {
                    val plp = run {
                        val f = plpTempFile()
                        if (!f.exists()) {
                            throw IllegalStateException(getString(R.string.temporary_pdf_not_found))
                        }
                        f
                    }
                    val named = exportNamedOutputPdf(plp)
                    named.absolutePath to named.name
                }

                hideInProgressIfShown()
                unlockScreenOrientation()

                val printManager = getSystemService(PrintManager::class.java)
                if (printManager == null) {
                    Toast.makeText(this@MainActivity, getString(R.string.print_service_not_available), Toast.LENGTH_LONG).show()
                    return@launch
                }

                val adapter = PdfDocumentAdapter(namedPath, namedName)
                printManager.print(namedName, adapter, PrintAttributes.Builder().build())

            } catch (e: Exception) {
                hideInProgressIfShown()
                unlockScreenOrientation()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.failed_to_generate_pdf_with_msg, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveNupPdf() {
        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        lifecycleScope.launch {
            try {
                val outputPasswordBytes: ByteArray? = if (passwordOutCb.isChecked) {
                    val pw = withContext(Dispatchers.Main) { getConfirmedOutputPasswordOrNull() }
                    if (pw == null) {
                        hideInProgressIfShown()
                        unlockScreenOrientation()
                        return@launch
                    }
                    pw
                } else null

                val (namedPath, namedName) = withContext(Dispatchers.IO) {
                    val plp = run {
                        val f = plpTempFile()
                        if (!f.exists()) {
                            throw IllegalStateException(getString(R.string.temporary_pdf_not_found))
                        }
                        f
                    }
                    val named = exportNamedOutputPdf(plp)

                    if (outputPasswordBytes != null) {
                        encryptPdfInPlace(named, outputPasswordBytes)
                    }

                    named.absolutePath to named.name
                }

                pendingSaveNamedPath = namedPath
                pendingSaveNamedDisplayName = namedName

                hideInProgressIfShown()
                unlockScreenOrientation()

                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_TITLE, namedName)
                }
                saveDocumentLauncher.launch(intent)

            } catch (e: Exception) {
                hideInProgressIfShown()
                unlockScreenOrientation()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.failed_to_generate_pdf_with_msg, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun shareNupPdf() {
        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        lifecycleScope.launch {
            try {
                val outputPasswordBytes: ByteArray? = if (passwordOutCb.isChecked) {
                    val pw = withContext(Dispatchers.Main) { getConfirmedOutputPasswordOrNull() }
                    if (pw == null) {
                        hideInProgressIfShown()
                        unlockScreenOrientation()
                        return@launch
                    }
                    pw
                } else null

                val plp = withContext(Dispatchers.IO) {
                    val f = plpTempFile()
                    if (!f.exists()) {
                        throw IllegalStateException(getString(R.string.temporary_pdf_not_found))
                    }
                    f
                }
                val named = withContext(Dispatchers.IO) {
                    val nf = exportNamedOutputPdf(plp)
                    if (outputPasswordBytes != null) {
                        encryptPdfInPlace(nf, outputPasswordBytes)
                    }
                    nf
                }

                val uri = withContext(Dispatchers.IO) {
                    FileProvider.getUriForFile(this@MainActivity, "$packageName.provider", named)
                }

                hideInProgressIfShown()
                unlockScreenOrientation()
                sharePdfWithViewOptions(uri)

            } catch (e: Exception) {
                hideInProgressIfShown()
                unlockScreenOrientation()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.failed_to_generate_share_pdf_with_msg, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun promptForPasswordForOutput(titleResId: Int, errorHintResId: Int? = null): String? =
        suspendCancellableCoroutine { cont ->
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_signature_chooser, null)
            val dialog = MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create()

            (dialogView as? ViewGroup)?.let { vg ->
                (vg.getChildAt(0) as? TextView)?.setText(titleResId)
            }

            val listView = dialogView.findViewById<ListView>(R.id.listView)
            val parent = listView.parent as ViewGroup
            val indexInParent = parent.indexOfChild(listView)
            val lp = listView.layoutParams

            val et = setupPasswordInputForDialog(
                dialog = dialog,
                dialogView = dialogView,
                parent = parent,
                indexInParent = indexInParent,
                lp = lp,
                showError = (errorHintResId != null),
                errorHintResId = errorHintResId
            )

            val btnNew    = dialogView.findViewById<Button>(R.id.btn_new)
            val btnRemove = dialogView.findViewById<Button>(R.id.btn_remove)
            val btnOk     = dialogView.findViewById<Button>(R.id.btn_ok)
            val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

            btnNew.visibility = View.GONE
            btnRemove.visibility = View.GONE

            btnOk.setOnClickListener {
                val pwd = et.text?.toString()?.trim().orEmpty()
                dialog.dismiss()
                if (!cont.isCompleted) cont.resume(pwd)
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
                if (!cont.isCompleted) cont.resume(null)
            }

            dialog.setOnCancelListener {
                if (!cont.isCompleted) cont.resume(null)
            }

            cont.invokeOnCancellation {
                if (dialog.isShowing) dialog.dismiss()
            }

            dialog.show()
        }

    private suspend fun getConfirmedOutputPasswordOrNull(): ByteArray? {
        var needErrorOnFirst = false
        while (true) {
            val first = promptForPasswordForOutput(
                titleResId = R.string.enter_pdf_password,
                errorHintResId = if (needErrorOnFirst) R.string.password_do_not_match else null
            ) ?: return null
            val second = promptForPasswordForOutput(
                titleResId = R.string.password_again,
                errorHintResId = null
            ) ?: return null

            if (first == second) {
                return first.toByteArray(Charsets.UTF_8)
            } else {
                needErrorOnFirst = true
            }
        }
    }

    private fun encryptPdfInPlace(file: File, password: ByteArray) {
        val tmp = File(file.parentFile, file.nameWithoutExtension + "_enc.pdf")

        val wp = writerProps().apply {
            setStandardEncryption(
                password,
                password,
                0,
                EncryptionConstants.ENCRYPTION_AES_256
            )
        }

        PdfDocument(
            PdfWriter(tmp.absolutePath, wp)
        ).use { dest ->
            dest.setFlushUnusedObjects(true)

            val batch = batchValueOrDefault1()
            PdfDocument(rdr(file.absolutePath)).use { src ->
                val total = src.numberOfPages
                var from = 1
                while (from <= total) {
                    val to = min(from + batch - 1, total)
                    src.copyPagesTo(from, to, dest)
                    runCatching { dest.flushCopiedObjects(src) }
                    val added = to - from + 1
                    for (i in 0 until added) {
                        val p = dest.numberOfPages - i
                        if (p >= 1) runCatching { dest.getPage(p).flush() }
                    }
                    from = to + 1
                }
            }
        }

        if (!file.delete()) {
            runCatching { tmp.delete() }
            throw IOException(getString(R.string.failed_to_replace_original_pdf_with_encrypted_version))
        }
        if (!tmp.renameTo(file)) {
            throw IOException(getString(R.string.failed_to_rename_encrypted_pdf))
        }
    }

    @Throws(Exception::class)
    private fun createNupPdf(outFile: File) {

        if (::adapter.isInitialized) runCatching { adapter.pausePrefetchAndCancel() }

        val writerProps = writerProps()

        try {

            val cfg = currentNupConfigFromInputs()
            val rows = cfg.rows
            val cols = cfg.cols
            val marginMm = cfg.marginMm
            val land = cfg.isLandscape
            val drawFrame = cfg.drawFrame
            val scaleIndRequested = cfg.scaleIndividually
            val basePs    = if (land) PageSize.A4.rotate()
            else PageSize.A4

            val source = sourceTempFile()
            if (!source.exists()) throw IllegalStateException(getString(R.string.no_source_pages))

            val totalSrcPages = PdfDocument(rdr(source.absolutePath)).use { it.numberOfPages }

            val marginPt = marginMm * 72f / 25.4f
            val per      = (rows * cols).coerceAtLeast(1)
            val isPortraitMode = !land
            val isSingleUp     = (per == 1)

            val totalOutPages = if (totalSrcPages == 0) 0 else (totalSrcPages + per - 1) / per
            if (totalOutPages == 0) {
                FileOutputStream(outFile).use {

                }
                return
            }

            val outPagesPerChunk = batchValueOrDefault1()
            val partFiles = mutableListOf<File>()

            var outPageStart = 0
            var chunkIndex = 0
            while (outPageStart < totalOutPages) {
                val outPageEndInclusive = min(totalOutPages - 1, outPageStart + outPagesPerChunk - 1)

                val srcStart = outPageStart * per
                val srcEnd   = min(totalSrcPages - 1, (outPageEndInclusive + 1) * per - 1)

                val partFile = File((outFile.parentFile ?: cacheDir), outFile.name + ".p" + chunkIndex.toString())

                PdfDocument(rdr(source.absolutePath)).use { src ->
                    PdfDocument(
                        PdfWriter(partFile.absolutePath, writerProps)
                    ).use { pdf ->
                        pdf.setFlushUnusedObjects(true)

                        var idxLocal = 0
                        var globalIdx = srcStart
                        while (globalIdx <= srcEnd) {
                            val page1Based = globalIdx + 1
                            val sp = src.getPage(page1Based)
                            val sz = sp.pageSize

                            if (idxLocal % per == 0) {
                                val psForThisPage =
                                    if (isPortraitMode && isSingleUp) PageSize.A4
                                    else basePs
                                pdf.addNewPage(psForThisPage)
                            }

                            val currentPage = pdf.lastPage
                            val canvas      = PdfCanvas(currentPage)

                            val psForThisPage = currentPage.pageSize
                            val cellW = (psForThisPage.width  - marginPt * (cols + 1)) / cols
                            val cellH = (psForThisPage.height - marginPt * (rows + 1)) / rows

                            val scale =
                                if (!scaleIndRequested) {
                                    val srcIsPortrait = sz.height >= sz.width
                                    val refW = if (srcIsPortrait) PageSize.A4.width else PageSize.A4.height
                                    val refH = if (srcIsPortrait) PageSize.A4.height else PageSize.A4.width
                                    min(min(cellW / refW, cellH / refH), 1f)
                                } else {
                                    min(cellW / sz.width, cellH / sz.height)
                                }

                            val col     = idxLocal % cols
                            val row     = (idxLocal / cols) % rows
                            val cellX   = marginPt + col * (cellW + marginPt)
                            val cellY   = marginPt + (rows - 1 - row) * (cellH + marginPt)

                            val finalScale: Float
                            if (isPortraitMode && isSingleUp) {
                                val fit = min(cellW / sz.width, cellH / sz.height)
                                val s = if (scaleIndRequested) fit else min(fit, 1f)
                                finalScale = if (!scaleIndRequested) {
                                    if (s < 1f) s else scale
                                } else {
                                    s
                                }
                            } else {
                                val fitToCell = min(cellW / sz.width, cellH / sz.height)
                                finalScale =
                                    if (!scaleIndRequested) {
                                        val fitNoUpscale = min(fitToCell, 1f)
                                        if (isSingleUp) {
                                            if (fitNoUpscale < 1f) fitNoUpscale else scale
                                        } else {
                                            val srcIsPortrait = sz.height >= sz.width
                                            val orientationMismatch =
                                                (land && srcIsPortrait) || (!land && !srcIsPortrait)

                                            if (!orientationMismatch) {
                                                min(scale, fitNoUpscale)
                                            } else {
                                                val fitW = cellW / sz.width
                                                val fitH = cellH / sz.height
                                                val limitIsWidth = fitW <= fitH
                                                val refW = if (srcIsPortrait) PageSize.A4.width else PageSize.A4.height
                                                val refH = if (srcIsPortrait) PageSize.A4.height else PageSize.A4.width
                                                val srcRel = if (limitIsWidth) (sz.width / refW) else (sz.height / refH)
                                                val threshold = 0.5f
                                                val preferFit = srcRel >= threshold
                                                val candidate = if (preferFit) fitNoUpscale else scale
                                                min(candidate, fitNoUpscale)
                                            }
                                        }
                                    } else {
                                        scale
                                    }
                            }

                            val offsetX = cellX + (cellW - sz.width * finalScale) / 2f
                            val offsetY = cellY + (cellH - sz.height * finalScale) / 2f

                            val fx  = sp.copyAsFormXObject(pdf)
                            canvas.saveState()
                            canvas.concatMatrix(
                                finalScale.toDouble(), 0.0, 0.0, finalScale.toDouble(),
                                offsetX.toDouble(), offsetY.toDouble()
                            )
                            canvas.addXObjectAt(fx, 0f, 0f)
                            runCatching { fx.flush() }
                            canvas.restoreState()

                            if (drawFrame) {
                                canvas.setStrokeColor(ColorConstants.BLACK)
                                    .setLineWidth(1f)
                                    .rectangle(cellX.toDouble(), cellY.toDouble(), cellW.toDouble(), cellH.toDouble())
                                    .stroke()
                            }

                            if (idxLocal % per == per - 1 || globalIdx == srcEnd) {
                                runCatching { currentPage.flush() }
                            }

                            idxLocal++
                            globalIdx++
                        }
                    }
                }

                partFiles += partFile
                outPageStart = outPageEndInclusive + 1
                chunkIndex++
            }

            PdfDocument(
                PdfWriter(outFile.absolutePath, writerProps)
            ).use { destDoc ->
                destDoc.setFlushUnusedObjects(true)
                val partBatch = batchValueOrDefault1()

                for (pf in partFiles) {
                    val total = PdfDocument(rdr(pf.absolutePath)).use { it.numberOfPages }

                    var from = 1
                    while (from <= total) {
                        val to = min(from + partBatch - 1, total)

                        PdfDocument(rdr(pf.absolutePath)).use { partDoc ->
                            partDoc.copyPagesTo(from, to, destDoc)
                            runCatching { destDoc.flushCopiedObjects(partDoc) }
                        }

                        val added = to - from + 1
                        for (i in 0 until added) {
                            val p = destDoc.numberOfPages - i
                            if (p >= 1) runCatching { destDoc.getPage(p).flush() }
                        }

                        from = to + 1
                    }
                }
            }

            partFiles.forEach { runCatching { it.delete() } }

        } finally {
            if (::adapter.isInitialized) runCatching { adapter.resumePrefetch() }
        }
    }

    private fun showTimestampDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_signature_chooser, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        (dialogView.findViewById<View>(R.id.signature_popup_title) as? TextView)
            ?.setText(R.string.add_timestamp)

        val listView = dialogView.findViewById<ListView>(R.id.listView)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        val options = listOf(
            getString(R.string.opt_year_yyyy) to "[YYYY]",
            getString(R.string.opt_year_yy)   to "[YY]",
            getString(R.string.opt_month_mm)  to "[MM]",
            getString(R.string.opt_day_dd)    to "[DD]",
            getString(R.string.opt_hour_hh)   to "[hh]",
            getString(R.string.opt_minute_mm) to "[mm]",
            getString(R.string.opt_second_ss) to "[ss]"
        )
        val labels = options.map { it.first }
        val checked = BooleanArray(labels.size) { false }

        run {
            data class RowViews(
                val text: MaterialTextView,
                val cb: MaterialCheckBox
            )
            fun Int.dp(parent: ViewGroup) = (this * parent.resources.displayMetrics.density).toInt()

            val adapter = object : BaseAdapter() {
                override fun getCount(): Int = labels.size
                override fun getItem(position: Int): Any = labels[position]
                override fun getItemId(position: Int): Long = position.toLong()

                @SuppressLint("RtlHardcoded")
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row = if (convertView == null) {
                        val container = LinearLayout(parent.context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            setPadding(16.dp(parent), 12.dp(parent), 16.dp(parent), 12.dp(parent))
                            isClickable = true
                            isFocusable = true
                        }
                        val tv = MaterialTextView(parent.context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                            )
                            textAlignment = View.TEXT_ALIGNMENT_GRAVITY
                            gravity = Gravity.LEFT
                            TextViewCompat.setTextAppearance(
                                this, R.style.TextAppearance_PdfLabelPrinting_ListItem
                            )
                        }
                        val cb = MaterialCheckBox(parent.context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { gravity = Gravity.RIGHT }
                            text = ""
                            isClickable = true
                            isFocusable = false

                            isUseMaterialThemeColors = false
                            buttonDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_checkbox)
                            buttonTintList = AppCompatResources.getColorStateList(context, R.color.ic_checkbox_filename_color)

                            background = null
                            contentDescription = null
                        }
                        container.addView(tv)
                        container.addView(cb)
                        container.tag = RowViews(tv, cb)
                        container
                    } else convertView

                    val holder = row.tag as RowViews
                    holder.text.text = labels[position]
                    holder.cb.isChecked = checked[position]

                    val onToggle: (Int) -> Unit = { idx ->
                        checked[idx] = !checked[idx]
                        holder.cb.isChecked = checked[idx]
                    }
                    row.setOnClickListener { onToggle(position) }
                    holder.cb.setOnClickListener { onToggle(position) }

                    return row
                }
            }

            listView.adapter = adapter
        }

        val btnNew    = dialogView.findViewById<Button>(R.id.btn_new)
        val btnRemove = dialogView.findViewById<Button>(R.id.btn_remove)
        val btnOk     = dialogView.findViewById<Button>(R.id.btn_ok)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

        btnNew?.visibility = View.GONE
        btnRemove?.visibility = View.GONE

        val fileNameEt: EditText = findViewById(R.id.file_name)

        btnOk?.setOnClickListener {
            val tokens = buildList {
                for (i in labels.indices) if (checked[i]) add(options[i].second)
            }
            if (tokens.isNotEmpty()) {
                val insert = tokens.joinToString(separator = "")
                val editable = fileNameEt.text ?: Editable.Factory.getInstance().newEditable("")
                val start = fileNameEt.selectionStart.coerceAtLeast(0).coerceAtMost(editable.length)
                editable.insert(start, insert)
                fileNameEt.text = editable
                fileNameEt.setSelection((start + insert.length).coerceAtMost(editable.length))
            }
            dialog.dismiss()
        }

        btnCancel?.setOnClickListener { dialog.dismiss() }

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    private fun buildOutputFileName(): String {
        val fileNameEt: EditText = findViewById(R.id.file_name)
        val tmpl = fileNameEt.text?.toString().orEmpty().trim()

        if (tmpl.isNotBlank()) return replaceTimeTokensIn(tmpl)

        val rowsStr = numberOfRows.text?.toString()?.trim().orEmpty()
        val colsStr = numberOfColumns.text?.toString()?.trim().orEmpty()
        val rows = rowsStr.toIntOrNull() ?: 1
        val cols = colsStr.toIntOrNull() ?: 1
        val isNormalByRowsCols = (rows in 0..1) && (cols in 0..1)

        val defTemplate = if (isNormalByRowsCols) {
            getString(R.string.default_tmpl_normal)
        } else {
            getString(R.string.default_tmpl_label)
        }

        return replaceTimeTokensIn(defTemplate)
    }

    private fun replaceTimeTokensIn(template: String): String {
        val now = Date()
        var s = template
        fun fmt(pat: String): String = SimpleDateFormat(pat, Locale.getDefault()).format(now)
        s = s.replace("[YYYY]", fmt("yyyy"))
        s = s.replace("[YY]",   fmt("yy"))
        s = s.replace("[MM]",   fmt("MM"))
        s = s.replace("[DD]",   fmt("dd"))
        s = s.replace("[hh]",   fmt("HH"))
        s = s.replace("[mm]",   fmt("mm"))
        s = s.replace("[ss]",   fmt("ss"))
        return s
    }

    private fun defaultDateForFileName(): String =
        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    private fun sanitizeFileNameBase(name: String): String {
        if (name.isBlank()) return defaultDateForFileName()

        val cleaned = buildString(name.length) {
            for (ch in name) {
                if (ch.code in 0x00..0x1F ||
                    ch == '"' || ch == '<' || ch == '>' || ch == '|' ||
                    ch == ':' || ch == '*' || ch == '?' || ch == '/' || ch == '\\'
                ) append('_') else append(ch)
            }
        }

        var trimmed = cleaned.trimEnd { it == ' ' || it == '.' }
        if (trimmed.isEmpty()) trimmed = "_"

        if (trimmed == "." || trimmed == "..") trimmed = "_$trimmed"
        val reserved = setOf(
            "CON","PRN","AUX","NUL",
            "COM1","COM2","COM3","COM4","COM5","COM6","COM7","COM8","COM9",
            "LPT1","LPT2","LPT3","LPT4","LPT5","LPT6","LPT7","LPT8","LPT9"
        )
        if (trimmed.uppercase() in reserved) trimmed = "_$trimmed"

        if (trimmed.length > MAX_BASE_LEN) trimmed = trimmed.take(MAX_BASE_LEN)

        return trimmed
    }

    private fun sharePdfWithViewOptions(uri: Uri) {
        val chooserTitle = getString(R.string.send_pdf_file)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val displayName = (buildOutputFileName().ifBlank { defaultDateForFileName() }) + ".pdf"
            putExtra(Intent.EXTRA_TITLE, displayName)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
        }

        val pm = packageManager

        if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
            val resolves = pm.queryIntentActivities(sendIntent, 0)
            val ownComponents = resolves
                .filter { it.activityInfo.packageName == packageName }
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
                .toTypedArray()

            val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
                if (ownComponents.isNotEmpty()) {
                    putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownComponents)
                }
            }
            try {
                startActivity(chooser)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.no_app_can_share_pdf), Toast.LENGTH_LONG).show()
            }
        } else {
            val resolves = pm.queryIntentActivities(sendIntent, 0)
            val ownComponents = resolves
                .filter { it.activityInfo.packageName == packageName }
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }

            ownComponents.forEach {
                pm.setComponentEnabledSetting(
                    it,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            try {
                startActivity(Intent.createChooser(sendIntent, chooserTitle))
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.no_app_can_share_pdf), Toast.LENGTH_LONG).show()
            } finally {
                Handler(Looper.getMainLooper()).postDelayed({
                    ownComponents.forEach { comp ->
                        pm.setComponentEnabledSetting(
                            comp,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }, 500)
            }
        }
    }

    private fun signSelectedPagesOnSourceTemp(
        signatureFilePath: String,
        inputXMM: Float,
        inputYMM: Float,
        applyScanEffect: Boolean,
        applyGrayscalePage: Boolean
    ) {
        showInProgress(getString(R.string.in_progress_my))
        lockScreenOrientation()

        if (::adapter.isInitialized) runCatching { adapter.pausePrefetchAndCancel() }

        lifecycleScope.launch(Dispatchers.IO) {
            val isLand = landscape.isChecked
            val a4Wmm = if (isLand) 297f else 210f
            val a4Hmm = if (isLand) 210f else 297f

            val refPx = currentSignatureRefPx()

            val sigBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(signatureFilePath, sigBounds)
            if (sigBounds.outWidth <= 0 || sigBounds.outHeight <= 0) {
                withContext(Dispatchers.Main) {
                    hideInProgressIfShown()
                    Toast.makeText(this@MainActivity, getString(R.string.signature_image_invalid), Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val selectedPlpIndices = viewModel.pageItems
                .mapIndexedNotNull { idx, it -> if (it.isSelected) idx else null }
                .sorted()

            if (selectedPlpIndices.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.select_a_page), Toast.LENGTH_SHORT).show()
                    hideInProgressIfShown()
                }
                return@launch
            }

            val plp = plpTempFile()
            if (!plp.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.select_a_page), Toast.LENGTH_SHORT).show()
                    hideInProgressIfShown()
                }
                return@launch
            }

            fun calcInSampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
                var sample = 1
                val halfW = srcW / 2
                val halfH = srcH / 2
                while (halfW / sample >= dstW && halfH / sample >= dstH) {
                    sample *= 2
                }
                return sample.coerceAtLeast(1)
            }

            fun applyScanEffectToBitmap(src: Bitmap): Bitmap {
                val w = src.width
                val h = src.height
                val out = createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)

                val cm = ColorMatrix().apply { setSaturation(0.95f) }
                val contrast = 1.05f
                val brightness = -5f
                val m = floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(m))
                val basePaint = Paint(
                    Paint.ANTI_ALIAS_FLAG
                            or Paint.DITHER_FLAG
                            or Paint.FILTER_BITMAP_FLAG
                ).apply {
                    colorFilter = ColorMatrixColorFilter(cm)
                }
                c.drawBitmap(src, 0f, 0f, basePaint)

                run {
                    val edgeAlpha = 12
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    p.shader = LinearGradient(
                        0f, 0f, 0f, h.toFloat(),
                        Color.argb(edgeAlpha, 0, 0, 0),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                    p.shader = LinearGradient(
                        0f, 0f, w.toFloat(), 0f,
                        Color.argb(edgeAlpha, 0, 0, 0),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                }

                return out
            }

            val writerProps = writerProps()
                .setPdfVersion(PdfVersion.PDF_2_0)
                .setFullCompressionMode(true)
                .setCompressionLevel(Deflater.BEST_COMPRESSION)

            val tmpSigned = mutableMapOf<Int, File>()

            try {
                ParcelFileDescriptor.open(plp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (idx in selectedPlpIndices) {
                            if (idx < 0 || idx >= renderer.pageCount) continue

                            renderer.openPage(idx).use { page: PdfRenderer.Page ->
                                val targetW: Int
                                val targetH: Int
                                if (!isLand) {
                                    val k = refPx / page.width.toFloat()
                                    targetW = refPx.toInt()
                                    targetH = max(1, round(page.height * k).toInt())
                                } else {
                                    val k = refPx / page.height.toFloat()
                                    targetH = refPx.toInt()
                                    targetW = max(1, round(page.width * k).toInt())
                                }

                                var pageBmp = createBitmap(
                                    targetW,
                                    targetH,
                                    Bitmap.Config.ARGB_8888
                                )
                                var pageCanvas = Canvas(pageBmp)
                                pageCanvas.drawColor(Color.WHITE)

                                val s = min(
                                    targetW.toFloat() / page.width.toFloat(),
                                    targetH.toFloat() / page.height.toFloat()
                                )
                                val dx = (targetW - page.width * s) / 2f
                                val dy = (targetH - page.height * s) / 2f
                                val mat = Matrix().apply {
                                    postScale(s, s)
                                    postTranslate(dx, dy)
                                }
                                page.render(pageBmp, null, mat, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                if (applyGrayscalePage) {
                                    val grayBmp = createBitmap(pageBmp.width, pageBmp.height, Bitmap.Config.ARGB_8888)
                                    val grayCanvas = Canvas(grayBmp)
                                    val cm = ColorMatrix().apply { setSaturation(0f) }
                                    val paint = Paint(
                                        Paint.ANTI_ALIAS_FLAG or
                                                Paint.DITHER_FLAG or
                                                Paint.FILTER_BITMAP_FLAG
                                    ).apply {
                                        colorFilter = ColorMatrixColorFilter(cm)
                                    }
                                    grayCanvas.drawBitmap(pageBmp, 0f, 0f, paint)
                                    if (!pageBmp.isRecycled) pageBmp.recycle()
                                    pageBmp = grayBmp
                                    pageCanvas = Canvas(pageBmp)
                                }

                                val pxPerMMX = targetW.toFloat() / a4Wmm
                                val pxPerMMY = targetH.toFloat() / a4Hmm
                                val pxX = inputXMM * pxPerMMX
                                val pxY = inputYMM * pxPerMMY

                                val refAxisPx = if (isLand) targetH.toFloat() else targetW.toFloat()
                                val sigScale = refAxisPx / refPx

                                val desiredSigW = max(1, round(sigBounds.outWidth  * sigScale).toInt())
                                val desiredSigH = max(1, round(sigBounds.outHeight * sigScale).toInt())

                                val sample = calcInSampleSize(sigBounds.outWidth, sigBounds.outHeight, desiredSigW, desiredSigH)
                                val sigOpts = BitmapFactory.Options().apply {
                                    inSampleSize = sample
                                    inPreferredConfig = Bitmap.Config.ARGB_8888
                                }
                                val sigBmp = BitmapFactory.decodeFile(signatureFilePath, sigOpts)
                                    ?: run {
                                        Log.w("MainActivity", "Signature decode failed at page $idx")
                                        if (!pageBmp.isRecycled) pageBmp.recycle()
                                        return@use
                                    }

                                val dst = RectF(pxX, pxY, pxX + desiredSigW, pxY + desiredSigH)
                                pageCanvas.drawBitmap(sigBmp, null, dst, null)
                                sigBmp.recycle()

                                val bitmapForExport: Bitmap =
                                    if (applyScanEffect) {
                                        val b = applyScanEffectToBitmap(pageBmp)
                                        if (!pageBmp.isRecycled) pageBmp.recycle()
                                        b
                                    } else {
                                        pageBmp
                                    }

                                val out = File(cacheDir, "${timeStamp}_signed_${System.currentTimeMillis()}_${idx}.pdf")
                                ByteArrayOutputStream().use { _ ->
                                    val wBmp = bitmapForExport.width
                                    val hBmp = bitmapForExport.height
                                    val intPixels = IntArray(wBmp * hBmp)
                                    bitmapForExport.getPixels(intPixels, 0, wBmp, 0, 0, wBmp, hBmp)
                                    val rgb = ByteArray(wBmp * hBmp * 3)
                                    var pIx = 0
                                    for (i in intPixels.indices) {
                                        val c = intPixels[i]
                                        rgb[pIx++] = ((c ushr 16) and 0xFF).toByte()
                                        rgb[pIx++] = ((c ushr 8) and 0xFF).toByte()
                                        rgb[pIx++] = (c and 0xFF).toByte()
                                    }
                                    val imageData = ImageDataFactory.create(
                                        wBmp,
                                        hBmp,
                                        3,
                                        8,
                                        rgb,
                                        null
                                    )

                                    val pageSize = if (isLand) PageSize.A4.rotate() else PageSize.A4

                                    PdfDocument(PdfWriter(out.absolutePath, writerProps)).use { destDoc: PdfDocument ->
                                        destDoc.setFlushUnusedObjects(true)

                                        val pg = destDoc.addNewPage(pageSize)

                                        val imgRatio = bitmapForExport.width.toFloat() / bitmapForExport.height.toFloat()
                                        val pageRatio = pageSize.width / pageSize.height
                                        val (destW, destH) = if (imgRatio > pageRatio) {
                                            val w = pageSize.width
                                            val h = w / imgRatio
                                            w to h
                                        } else {
                                            val h = pageSize.height
                                            val w = h * imgRatio
                                            w to h
                                        }
                                        val offX = (pageSize.width - destW) / 2f
                                        val offY = (pageSize.height - destH) / 2f

                                        PdfCanvas(pg).addImageFittedIntoRectangle(
                                            imageData,
                                            Rectangle(offX, offY, destW, destH),
                                            false
                                        )
                                        pg.flush()
                                    }
                                }

                                tmpSigned[idx] = out

                                if (!bitmapForExport.isRecycled) bitmapForExport.recycle()
                            }
                        }
                    }
                }

                val plpWork = File(cacheDir, "${PLP_TEMP_NAME}.work")
                if (plpWork.exists()) plpWork.delete()

                PdfDocument(PdfWriter(plpWork.absolutePath, writerProps)).use { destDoc ->
                    destDoc.setFlushUnusedObjects(true)

                    val pageBatch = batchValueOrDefault1()

                    fun copyRange1BasedPaged(srcPath: String, from1: Int, to1: Int) {
                        var from = from1
                        while (from <= to1) {
                            val to = min(from + pageBatch - 1, to1)
                            PdfDocument(rdr(srcPath)).use { src ->
                                src.copyPagesTo(from, to, destDoc)
                                runCatching { destDoc.flushCopiedObjects(src) }
                            }
                            val added = to - from + 1
                            for (i in 0 until added) {
                                val p = destDoc.numberOfPages - i
                                if (p >= 1) runCatching { destDoc.getPage(p).flush() }
                            }
                            from = to + 1
                        }
                    }

                    val totalPlpPages = PdfDocument(rdr(plp.absolutePath)).use { it.numberOfPages }

                    var runStart: Int? = null
                    for (p in 1..totalPlpPages) {
                        val zeroIdx = p - 1
                        val replacement = tmpSigned[zeroIdx]
                        if (replacement != null) {
                            runStart?.let { copyRange1BasedPaged(plp.absolutePath, it, p - 1) }
                            runStart = null

                            PdfDocument(rdr(replacement.absolutePath)).use { replDoc ->
                                replDoc.copyPagesTo(1, 1, destDoc)
                                runCatching { destDoc.flushCopiedObjects(replDoc) }
                            }
                            runCatching { destDoc.lastPage?.flush() }
                        } else {
                            if (runStart == null) runStart = p
                        }
                    }
                    runStart?.let { copyRange1BasedPaged(plp.absolutePath, it, totalPlpPages) }
                }

                if (plp.exists()) plp.delete()
                plpWork.renameTo(plp)

                val signedList = tmpSigned.entries.map { it.key to it.value.absolutePath }
                tmpSigned.clear()

                withContext(Dispatchers.Main) {
                    updateButtonsState()
                    currentSigFile = null
                    updateSignatureInputFilters()
                }

                updateSourceWithSignedGroups(signedList){
                    hideInProgressIfShown()
                    unlockScreenOrientation()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.signing_failed_with_msg, e.message), Toast.LENGTH_LONG).show()
                    hideInProgressIfShown()
                    unlockScreenOrientation()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    if (::adapter.isInitialized) runCatching { adapter.resumePrefetch() }
                    unlockScreenOrientation()
                }
            }
        }
    }

    private fun updateSourceWithSignedGroups(
        signedList: List<Pair<Int, String>>,
        onReloadStarted: (() -> Unit)? = null
    ) {
        try {
            val srcFile = sourceTempFile()
            if (!srcFile.exists()) return

            val writerProps = writerProps()
            val signedMap = signedList.toMap()

            val outWorkSrc = sourceWorkFile()
            if (outWorkSrc.exists()) outWorkSrc.delete()

            val pageBatch = batchValueOrDefault1()

            fun copyRange1BasedPaged(srcPath: String, dest: PdfDocument, from1: Int, to1: Int) {
                var from = from1
                while (from <= to1) {
                    val to = min(from + pageBatch - 1, to1)
                    PdfDocument(rdr(srcPath)).use { src ->
                        src.copyPagesTo(from, to, dest)
                        runCatching { dest.flushCopiedObjects(src) }
                    }
                    val added = to - from + 1
                    for (i in 0 until added) {
                        val p = dest.numberOfPages - i
                        if (p >= 1) runCatching { dest.getPage(p).flush() }
                    }
                    from = to + 1
                }
            }

            PdfDocument(PdfWriter(outWorkSrc.absolutePath, writerProps)).use { destSrcDoc ->
                destSrcDoc.setFlushUnusedObjects(true)

                val totalPages = PdfDocument(rdr(srcFile.absolutePath)).use { it.numberOfPages }
                val per = perGroup()

                var p = 1
                var runStart: Int? = null

                while (p <= totalPages) {
                    val groupIndex = (p - 1) / per
                    val replPath = signedMap[groupIndex]

                    if (replPath != null) {
                        runStart?.let { copyRange1BasedPaged(srcFile.absolutePath, destSrcDoc, it, p - 1) }
                        runStart = null

                        PdfDocument(rdr(replPath)).use { repl ->
                            repl.copyPagesTo(1, 1, destSrcDoc)
                            runCatching { destSrcDoc.flushCopiedObjects(repl) }
                        }
                        runCatching { destSrcDoc.lastPage?.flush() }

                        val groupStart = groupIndex * per + 1
                        val groupEnd = min(totalPages, groupStart + per - 1)
                        p = groupEnd + 1
                    } else {
                        if (runStart == null) runStart = p
                        p++
                    }
                }

                runStart?.let { copyRange1BasedPaged(srcFile.absolutePath, destSrcDoc, it, totalPages) }
            }

            if (srcFile.exists()) srcFile.delete()
            outWorkSrc.renameTo(srcFile)

        } catch (_: Exception) {

        } finally {
            runCatching {
                signedList.forEach { (_, path) -> runCatching { File(path).delete() } }
            }

            scheduleNupRebuild(onReloadStarted)
        }
    }

    private fun scheduleNupRebuild(onReloadStarted: (() -> Unit)? = null) {
        runCatching {
            if (::progressDialog.isInitialized && progressDialog.isShowing) {
                lockScreenOrientation()
            }
        }.onFailure {

        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                createTempPdfInternal()
            } finally {
                withContext(Dispatchers.Main) {
                    resetThumbDocKey("nup_rebuilt_${System.currentTimeMillis()}")

                    var unlockedFromOnStarted = false

                    reloadThumbnailsFromPlp(
                        releaseUiAtStart = false,
                        onStarted = {
                            unlockScreenOrientation()
                            unlockedFromOnStarted = true
                            onReloadStarted?.invoke()
                        }
                    )

                    updateButtonsState()

                    if (!unlockedFromOnStarted) {
                        unlockScreenOrientation()
                    }
                }
            }
        }
    }

    private class SimpleTextWatcher(private val cb: () -> Unit) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { cb() }
    }

    private fun setThemedBackArrow() {
        binding.toolbar.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back_24)
    }

    override fun onResume() {
        super.onResume()
        setThemedBackArrow()
        suppressNup = false
        runCatching { snapshotInitialPrefsIfNeeded() }

        if (Build.VERSION.SDK_INT <= VERSION_CODES.N_MR1 && !didRunNScrollColdStartFix && isFirstCreate) {
            val sv = binding.rootScroll

            sv.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            sv.isFocusable = true
            sv.isFocusableInTouchMode = true

            sv.post {
                sv.scrollTo(0, 0)
                sv.fullScroll(View.FOCUS_UP)

                sv.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
                sv.requestFocusFromTouch()

                didRunNScrollColdStartFix = true
            }
        }

        if (Build.VERSION.SDK_INT <= VERSION_CODES.N_MR1 && !isFirstCreate) {
            val sv = binding.rootScroll

            sv.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

            sv.post {
                sv.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS

                sv.isFocusable = true
                sv.isFocusableInTouchMode = true
                sv.requestFocusFromTouch()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        MenuCompat.setGroupDividerEnabled(menu, true)

        runCatching {
            val item = menu.findItem(R.id.action_inner_pdf_reader)
            if (item != null) {
                val prefs = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
                val enabled = prefs.getBoolean(KEY_LAST_INNER_PDF_READER, true)
                item.isCheckable = true
                item.isChecked = enabled
                item.isEnabled = Build.VERSION.SDK_INT >= VERSION_CODES.S
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_theme_light -> { setThemeMode("light"); true }
            R.id.action_theme_dark  -> { setThemeMode("dark");  true }
            R.id.action_theme_system-> { setThemeMode("system");true }

            R.id.action_inner_pdf_reader -> {
                val newChecked = !item.isChecked
                item.isChecked = newChecked
                getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
                    .edit { putBoolean(KEY_LAST_INNER_PDF_READER, newChecked) }
                true
            }

            R.id.action_save -> {
                launchSaveSharedPreferences()
                true
            }

            R.id.action_load -> {
                launchLoadSharedPreferences()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun launchSaveSharedPreferences() {
        val suggestedName = "PLP_settings.xml"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/xml"
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }
        savePrefsLauncher.launch(intent)
    }

    private fun launchLoadSharedPreferences() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/xml",
                    "text/xml",
                    "text/plain",
                    "application/octet-stream"
                )
            )
        }
        loadPrefsLauncher.launch(intent)
    }

    private fun exportAllSharedPreferencesXml(): String {
        val serializer = Xml.newSerializer()
        val sw = StringWriter()

        serializer.setOutput(sw)
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "preferences")

        fun writeMap(name: String) {
            val all = getSharedPreferences(name, MODE_PRIVATE).all
            serializer.startTag(null, "map")
            serializer.attribute(null, "name", name)

            val allowedKeys = setOf(
                KEY_NORMAL_ROWS, KEY_LABEL_ROWS,
                KEY_NORMAL_COLUMNS, KEY_LABEL_COLUMNS,
                KEY_NORMAL_BATCH_PAGE, KEY_LABEL_BATCH_PAGE,
                KEY_NORMAL_SCALE, KEY_LABEL_SCALE,
                KEY_NORMAL_MARGIN, KEY_LABEL_MARGIN,
                KEY_NORMAL_ORIENTATION, KEY_LABEL_ORIENTATION,
                KEY_NORMAL_DRAW_FRAME, KEY_LABEL_DRAW_FRAME,
                KEY_NORMAL_SIG_POS_X, KEY_LABEL_SIG_POS_X,
                KEY_NORMAL_SIG_POS_Y, KEY_LABEL_SIG_POS_Y,
                KEY_NORMAL_PASSWORD_OUT, KEY_LABEL_PASSWORD_OUT,
                KEY_NORMAL_FILE_NAME_TEMPLATE, KEY_LABEL_FILE_NAME_TEMPLATE,
                KEY_LAST_SIG_DPI_INDEX, KEY_LAST_SCAN_EFFECT, KEY_LAST_GRAYSCALE,
                KEY_LAST_INNER_PDF_READER, KEY_LAST_LOADED_PROFILE,
                KEY_RESTORE_LOCK_UNTIL_RELOAD, KEY_FIRST_RUN_INITIALIZED
            )

            for ((key, value) in all) {
                if (!allowedKeys.contains(key)) continue

                serializer.startTag(null, "entry")
                serializer.attribute(null, "key", key)
                when (value) {
                    is String -> { serializer.attribute(null, "type", "string"); serializer.text(value) }
                    is Int -> { serializer.attribute(null, "type", "int"); serializer.text(value.toString()) }
                    is Long -> { serializer.attribute(null, "type", "long"); serializer.text(value.toString()) }
                    is Float -> { serializer.attribute(null, "type", "float"); serializer.text(value.toString()) }
                    is Boolean -> { serializer.attribute(null, "type", "boolean"); serializer.text(value.toString()) }
                    is Set<*> -> {
                        val items = value.filterIsInstance<String>()
                        serializer.attribute(null, "type", "string_set")
                        serializer.text(items.joinToString("\u0001"))
                    }
                    else -> {

                    }
                }
                serializer.endTag(null, "entry")
            }
            serializer.endTag(null, "map")
        }

        writeMap(PREFS_NAME_FRAGMENT)

        serializer.endTag(null, "preferences")
        serializer.endDocument()

        return sw.toString()
    }

    private fun importAllSharedPreferencesFromXml(xml: String) {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        val spFragment = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)

        var editor: SharedPreferences.Editor? = null
        var inTargetMap = false

        var currentKey: String? = null
        var currentType: String? = null
        val textBuf = StringBuilder()

        var foundTargetMap = false

        val allowedKeys = setOf(
            KEY_NORMAL_ROWS, KEY_LABEL_ROWS,
            KEY_NORMAL_COLUMNS, KEY_LABEL_COLUMNS,
            KEY_NORMAL_BATCH_PAGE, KEY_LABEL_BATCH_PAGE,
            KEY_NORMAL_SCALE, KEY_LABEL_SCALE,
            KEY_NORMAL_MARGIN, KEY_LABEL_MARGIN,
            KEY_NORMAL_ORIENTATION, KEY_LABEL_ORIENTATION,
            KEY_NORMAL_DRAW_FRAME, KEY_LABEL_DRAW_FRAME,
            KEY_NORMAL_SIG_POS_X, KEY_LABEL_SIG_POS_X,
            KEY_NORMAL_SIG_POS_Y, KEY_LABEL_SIG_POS_Y,
            KEY_NORMAL_PASSWORD_OUT, KEY_LABEL_PASSWORD_OUT,
            KEY_NORMAL_FILE_NAME_TEMPLATE, KEY_LABEL_FILE_NAME_TEMPLATE,
            KEY_LAST_SIG_DPI_INDEX, KEY_LAST_SCAN_EFFECT, KEY_LAST_GRAYSCALE,
            KEY_LAST_INNER_PDF_READER, KEY_LAST_LOADED_PROFILE,
            KEY_RESTORE_LOCK_UNTIL_RELOAD, KEY_FIRST_RUN_INITIALIZED
        )

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "map" -> {
                            val nameAttr = (0 until parser.attributeCount)
                                .firstOrNull { parser.getAttributeName(it) == "name" }
                                ?.let { parser.getAttributeValue(it) }
                            inTargetMap = (nameAttr == PREFS_NAME_FRAGMENT)
                            if (inTargetMap) {
                                foundTargetMap = true
                                editor = spFragment.edit()
                            }
                        }
                        "entry" -> {
                            if (inTargetMap) {
                                currentKey = (0 until parser.attributeCount)
                                    .firstOrNull { parser.getAttributeName(it) == "key" }
                                    ?.let { parser.getAttributeValue(it) }
                                currentType = (0 until parser.attributeCount)
                                    .firstOrNull { parser.getAttributeName(it) == "type" }
                                    ?.let { parser.getAttributeValue(it) }
                                textBuf.setLength(0)
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTargetMap) {
                        textBuf.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "entry" -> {
                            if (inTargetMap) {
                                val key = currentKey
                                val type = currentType
                                val valueText = textBuf.toString()

                                if (key != null && type != null && editor != null) {
                                    if (allowedKeys.contains(key)) {
                                        when (type) {
                                            "string"     -> editor.putString(key, valueText)
                                            "int"        -> editor.putInt(key, valueText.toIntOrNull() ?: 0)
                                            "long"       -> editor.putLong(key, valueText.toLongOrNull() ?: 0L)
                                            "float"      -> editor.putFloat(key, valueText.toFloatOrNull() ?: 0f)
                                            "boolean"    -> editor.putBoolean(key, valueText.equals("true", ignoreCase = true))
                                            "string_set" -> {
                                                val set = if (valueText.isEmpty()) emptySet() else valueText.split('\u0001').toSet()
                                                editor.putStringSet(key, set)
                                            }
                                        }
                                    }
                                }
                                currentKey = null
                                currentType = null
                                textBuf.setLength(0)
                            }
                        }
                        "map" -> {
                            if (inTargetMap) {
                                inTargetMap = false
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        if (foundTargetMap) {
            editor?.apply()
        } else {
            throw IllegalArgumentException(getString(R.string.error_invalid_prefs_file, PREFS_NAME_FRAGMENT))
        }
    }

    private fun setThemeMode(mode: String) {
        val prefs = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
        prefs.edit { putString(KEY_THEME_MODE, mode) }

        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        runCatching {
            val lm = binding.rvThumbnails.layoutManager as? LinearLayoutManager
            val first = lm?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            val offset = if (first != RecyclerView.NO_POSITION) {
                lm?.findViewByPosition(first)?.top ?: 0
            } else 0
            val selectedIdx = viewModel.pageItems
                .mapIndexedNotNull { idx, it -> if (it.isSelected) idx else null }

            val nsv = findViewById<NestedScrollView>(R.id.root_scroll)
            val nsvY = nsv?.scrollY ?: -1

            val sp = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
            sp.edit {
                putInt(KEY_THEME_RESTORE_FIRST, first)
                putInt(KEY_THEME_RESTORE_OFFSET, offset)
                putString(KEY_THEME_RESTORE_SELECTED_CSV, selectedIdx.joinToString(","))
                putInt(KEY_THEME_RESTORE_NSV_Y, nsvY)
            }
        }

        runCatching {
            val sp2 = getSharedPreferences(PREFS_NAME_FRAGMENT, MODE_PRIVATE)
            sp2.edit { putBoolean(KEY_RESTORE_LOCK_UNTIL_RELOAD, true) }
        }.onFailure {

        }

        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}

@Suppress("DEPRECATION")
fun Intent.getParcelableCompat(name: String): Uri? =
    if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) getParcelableExtra(name, Uri::class.java)
    else getParcelableExtra(name)

@Suppress("DEPRECATION")
fun Intent.getParcelableArrayListCompat(name: String): ArrayList<Uri>? =
    if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) getParcelableArrayListExtra(name, Uri::class.java)
    else getParcelableArrayListExtra(name)