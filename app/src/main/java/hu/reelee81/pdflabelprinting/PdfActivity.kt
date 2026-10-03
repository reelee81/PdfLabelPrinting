package hu.reelee81.pdflabelprinting

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlin.math.ceil

abstract class PdfActivity : AppCompatActivity() {
    private var lastConfiguration: Configuration? = null
    private var pendingRecreation = false
    private var recreationPosted = false
    private var waitOverlay: View? = null
    private var waitDots: TextView? = null
    private var waitAnimationRunning = false
    private var waitDotCount = 0
    private var focusBeforeNavigation: View? = null
    private val blockedContent = mutableListOf<BlockedContentState>()
    private val cancelNavigationOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (!isPdfSavingInProgress()) finish()
        }
    }
    private val animateWaitDots = object : Runnable {
        override fun run() {
            if (!waitAnimationRunning) return
            waitDotCount = waitDotCount % 3 + 1
            waitDots?.text = ".".repeat(waitDotCount)
            waitDots?.postDelayed(this, 400)
        }
    }
    private val recreateWhenIdle = Runnable {
        recreationPosted = false
        if (pendingRecreation && !isFinishing && !isDestroyed &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            !supportFragmentManager.isStateSaved && !isPdfOperationInProgress()
        ) {
            pendingRecreation = false
            recreate()
        }
    }

    protected abstract fun isPdfNavigationInProgress(): Boolean

    protected open fun isPdfSavingInProgress(): Boolean = false

    protected open fun isPdfOperationInProgress(): Boolean = isPdfNavigationInProgress()

    protected open fun onPdfNavigationStateChanged(inProgress: Boolean) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, cancelNavigationOnBack)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                onPdfOperationStateChanged()
            }

            override fun onPause(owner: LifecycleOwner) {
                stopWaitAnimation()
            }
        })
        lastConfiguration = Configuration(resources.configuration)
        pendingRecreation = savedInstanceState?.getBoolean(STATE_PENDING_RECREATION) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PENDING_RECREATION, pendingRecreation)
        super.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val changes = lastConfiguration?.diff(newConfig) ?: 0
        lastConfiguration = Configuration(newConfig)
        super.onConfigurationChanged(newConfig)

        findViewById<View>(R.id.overlay_dialog_pdf_editor_discard_panel)?.let { panel ->
            val params = panel.layoutParams as ConstraintLayout.LayoutParams
            params.matchConstraintMaxWidth = resources.getDimensionPixelSize(R.dimen.dp_9999_overlay)
            panel.layoutParams = params
        }

        // PdfView beta01 loses forced scrubber visibility on in-place rotation.
        // Reinflate after navigation/saving finishes, but not for window-bounds-only changes.
        var resourceChanges = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_UI_MODE or
            ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_LAYOUT_DIRECTION or ActivityInfo.CONFIG_FONT_SCALE or
            ActivityInfo.CONFIG_DENSITY or ActivityInfo.CONFIG_MCC or ActivityInfo.CONFIG_MNC or
            ActivityInfo.CONFIG_KEYBOARD or ActivityInfo.CONFIG_NAVIGATION or ActivityInfo.CONFIG_TOUCHSCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            resourceChanges = resourceChanges or ActivityInfo.CONFIG_COLOR_MODE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            resourceChanges = resourceChanges or ActivityInfo.CONFIG_FONT_WEIGHT_ADJUSTMENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resourceChanges = resourceChanges or ActivityInfo.CONFIG_GRAMMATICAL_GENDER
        }
        if (changes and resourceChanges != 0) pendingRecreation = true
        onPdfOperationStateChanged()
    }

    fun onPdfOperationStateChanged() {
        if (isFinishing || isDestroyed) return
        val navigating = isPdfNavigationInProgress()
        val saving = isPdfSavingInProgress()
        cancelNavigationOnBack.isEnabled = navigating || saving
        onPdfNavigationStateChanged(navigating)
        updateWaitOverlay(navigating, saving)
        if (!pendingRecreation || recreationPosted || isFinishing || isDestroyed) return
        recreationPosted = true
        window.decorView.post(recreateWhenIdle)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        isPdfSavingInProgress() ||
            waitOverlay?.isVisible == true &&
                findViewById<View>(R.id.overlay_dialog_pdf_editor_discard)?.isVisible != true ||
            super.dispatchTouchEvent(event)

    override fun finish() {
        stopWaitAnimation()
        super.finish()
    }

    private fun updateWaitOverlay(navigating: Boolean, saving: Boolean) {
        val overlay = waitOverlay ?: findViewById<View>(R.id.overlay_pdf_navigation_wait)
            ?.also { waitOverlay = it } ?: return
        val waiting = navigating || saving
        val caption = getString(if (saving) R.string.pdf_edits_saving else R.string.pdf_navigation_wait)
        findViewById<TextView>(R.id.pdf_navigation_wait_caption).text = caption
        overlay.contentDescription = caption
        val dots = waitDots ?: findViewById<TextView>(R.id.pdf_navigation_wait_dots)
            .also {
                waitDots = it
                it.minWidth = ceil(it.paint.measureText(getString(R.string.pdf_navigation_wait_dots))).toInt()
            }

        if (waiting && blockedContent.isEmpty()) {
            focusBeforeNavigation = currentFocus
            for (id in listOf(R.id.appbar, R.id.pdfFragContainer)) {
                val content = findViewById<ViewGroup>(id)
                blockedContent.add(BlockedContentState(content, content.importantForAccessibility, content.descendantFocusability))
                content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
            overlay.isVisible = true
            overlay.requestFocus()
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        } else if (!waiting) {
            overlay.isVisible = false
            for ((view, accessibility, focusability) in blockedContent) {
                view.importantForAccessibility = accessibility
                view.descendantFocusability = focusability
            }
            blockedContent.clear()
            focusBeforeNavigation?.takeIf { it.isAttachedToWindow }?.requestFocus()
            focusBeforeNavigation = null
        }

        if (waiting && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (!waitAnimationRunning) {
                waitAnimationRunning = true
                waitDotCount = 0
                dots.post(animateWaitDots)
            }
        } else stopWaitAnimation()
    }

    private fun stopWaitAnimation() {
        waitAnimationRunning = false
        waitDots?.removeCallbacks(animateWaitDots)
    }

    override fun onDestroy() {
        stopWaitAnimation()
        blockedContent.clear()
        focusBeforeNavigation = null
        window.decorView.removeCallbacks(recreateWhenIdle)
        super.onDestroy()
    }

    private data class BlockedContentState(val view: ViewGroup, val accessibility: Int, val focusability: Int)

    private companion object {
        const val STATE_PENDING_RECREATION = "pdf_pending_recreation"
    }
}