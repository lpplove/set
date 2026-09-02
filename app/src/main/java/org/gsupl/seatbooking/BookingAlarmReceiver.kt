package org.gsupl.seatbooking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 闹钟到点了 → 启动 BookingExecutorService（Wakeful 执行）
 * 同时确保 KeepAliveService 前台服务还在。
 */
class BookingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isAfternoon = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_AFTERNOON, false)
        Prefs.appendLog("🔔 闹钟触发【${if (isAfternoon) "下午场" else "上午场"}】")

        // 先拉起前台保活
        ContextCompat.startForegroundService(
            context, Intent(context, KeepAliveService::class.java)
        )

        // 启动执行服务（含 WakeLock）
        val exec = Intent(context, BookingExecutorService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_IS_AFTERNOON, isAfternoon)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(exec)
        } else {
            context.startService(exec)
        }
    }
}
