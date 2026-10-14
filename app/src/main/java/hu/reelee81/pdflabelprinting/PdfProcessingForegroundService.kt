package hu.reelee81.pdflabelprinting

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class PdfProcessingForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "PdfProcessingForegroundServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        private const val EXTRA_PROGRESS = "EXTRA_PROGRESS"
        const val PROGRESS_NONE = -2
        const val PROGRESS_INDETERMINATE = -1

        fun start(context: Context, message: String, progress: Int = PROGRESS_NONE) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, PdfProcessingForegroundService::class.java).apply {
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_PROGRESS, progress)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    appCtx.startService(intent)
                } catch (_: IllegalStateException) {
                    ContextCompat.startForegroundService(appCtx, intent)
                }
            } else {
                appCtx.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PdfProcessingForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: getString(R.string.in_progress_my)
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, PROGRESS_NONE) ?: PROGRESS_NONE

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_statusbar_icon_24)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        when {
            progress >= 0 -> builder
                .setProgress(100, progress, false)
                .setSubText(getString(R.string.progress_percent, progress))
            progress == PROGRESS_INDETERMINATE -> builder.setProgress(0, 0, true)
        }
        val notification: Notification = builder.build()

        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.in_progress_my),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}