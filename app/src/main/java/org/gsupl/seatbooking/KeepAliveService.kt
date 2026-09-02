package org.gsupl.seatbooking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * 前台保活服务：常驻通知栏，显示下一次闹钟和最近状态，降低被系统杀死概率。
 */
class KeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        startForeground(NOTIF_ID, buildNotification(this, "APP保活中…", "等待下一次预约闹钟"))
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshNotification(this)
        AlarmScheduler.scheduleAll(this)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SeatBooking::KeepAlive"
        ).apply {
            setReferenceCounted(false)
            if (!isHeld) acquire(10 * 365 * 24 * 3600 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) { }
        wakeLock = null
    }

    companion object {
        const val CHANNEL_ID = "seat_booking_keep"
        const val NOTIF_ID = 1001

        @JvmStatic
        fun start(context: Context) {
            val i = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        private fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                    val c = NotificationChannel(
                        CHANNEL_ID,
                        "座位预约·保活通知",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "常驻通知栏，确保定时预约不被系统杀死"
                        setShowBadge(false)
                        enableVibration(false)
                    }
                    mgr.createNotificationChannel(c)
                }
            }
        }

        @JvmStatic
        fun refreshNotification(ctx: Context) {
            try {
                val line1 = "🌅 上午: ${Prefs.lastMorningStatus}   🌆 下午: ${Prefs.lastAfternoonStatus}"
                val line2 = "点击打开APP配置（每天自动预约）"
                val notif = buildNotification(ctx, line1, line2)
                val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                mgr.notify(NOTIF_ID, notif)
            } catch (_: Exception) { }
        }

        private fun buildNotification(ctx: Context, title: String, text: String): Notification {
            createChannel(ctx)
            val pi = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(pi)
                .build()
        }
    }
}
