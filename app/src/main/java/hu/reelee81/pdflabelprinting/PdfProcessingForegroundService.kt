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

        fun start(context: Context, message: String) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, PdfProcessingForegroundService::class.java).apply {
                putExtra(EXTRA_MESSAGE, message)
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

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_statusbar_icon_24)
            .setOngoing(true)
            .build()

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