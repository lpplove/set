package org.gsupl.seatbooking

import android.app.Application
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)

        // 提前创建通知渠道（Android 8+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                android.app.NotificationChannel(
                    KeepAliveService.CHANNEL_ID,
                    "座位预约·保活通知",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) },
                android.app.NotificationChannel(
                    BookingExecutorService.EXEC_CHANNEL_ID,
                    "座位预约·执行中",
                    NotificationManager.IMPORTANCE_LOW
                ),
                android.app.NotificationChannel(
                    BookingExecutorService.RESULT_CHANNEL_ID,
                    "座位预约·结果",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            channels.forEach {
                if (nm.getNotificationChannel(it.id) == null) nm.createNotificationChannel(it)
            }
        }

        // 启动前台保活 + 配置闹钟
        KeepAliveService.start(this)
        AlarmScheduler.scheduleAll(this)
    }
}
