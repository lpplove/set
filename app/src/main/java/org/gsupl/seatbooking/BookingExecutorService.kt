package org.gsupl.seatbooking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * 到点后真正执行预约的服务：
 *   自己作为前台服务运行（显示"正在预约中…"），
 *   用 WakeLock 保证手机不会在中途睡着。
 *   执行完 → 重新调度下一天闹钟 → 发送"完成"通知 → stopSelf。
 */
class BookingExecutorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        startForeground(
            EXEC_NOTIF_ID,
            buildExecNotification(this, "准备开始预约…")
        )
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isAfternoon = intent?.getBooleanExtra(AlarmScheduler.EXTRA_IS_AFTERNOON, false) ?: false
        scope.launch {
            try {
                updateStatus("正在预约【${if (isAfternoon) "下午场" else "上午场"}】…")
                val ok = BookingEngine.runBooking(this@BookingExecutorService, isAfternoon)
                sendResultNotification(if (ok) "✅ 预约成功" else "❌ 预约失败，请打开APP查看日志")
            } catch (t: Throwable) {
                Prefs.appendLog("🔥 执行服务异常: ${t.message}")
                sendResultNotification("🔥 预约异常: ${t.message?.take(40)}")
            } finally {
                // 重新调度下一天
                AlarmScheduler.rescheduleAfterRun(this@BookingExecutorService, isAfternoon)
                KeepAliveService.refreshNotification(this@BookingExecutorService)
                releaseWakeLock()
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateStatus(text: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(EXEC_NOTIF_ID, buildExecNotification(this, text))
    }

    private fun sendResultNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val resNotif = NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("座位预约·执行结果")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { nm.notify(RESULT_NOTIF_ID, resNotif) } catch (_: Exception) {}
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SeatBooking::Booking"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 最多持锁10分钟，足够跑30次循环
        }
    }
    private fun releaseWakeLock() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
    }

    companion object {
        const val EXEC_CHANNEL_ID = "seat_booking_exec"
        const val EXEC_NOTIF_ID = 1010
        const val RESULT_CHANNEL_ID = "seat_booking_result"
        const val RESULT_NOTIF_ID = 1011

        private fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                listOf(
                    NotificationChannel(EXEC_CHANNEL_ID, "座位预约·执行中", NotificationManager.IMPORTANCE_LOW),
                    NotificationChannel(RESULT_CHANNEL_ID, "座位预约·结果", NotificationManager.IMPORTANCE_HIGH)
                ).forEach { c ->
                    if (mgr.getNotificationChannel(c.id) == null) {
                        c.enableVibration(true)
                        mgr.createNotificationChannel(c)
                    }
                }
            }
        }

        private fun buildExecNotification(ctx: Context, text: String): Notification {
            createChannel(ctx)
            return NotificationCompat.Builder(ctx, EXEC_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("座位预约·执行中")
                .setContentText(text)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
}
