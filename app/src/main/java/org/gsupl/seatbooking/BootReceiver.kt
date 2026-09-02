package org.gsupl.seatbooking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 开机自启 / 应用更新后 → 重启前台服务 + 重新设置闹钟
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: ""
        Prefs.appendLog("📱 收到系统广播: $action，重新初始化…")

        // 拉起前台服务（获取 WakeLock + 常驻通知）
        ContextCompat.startForegroundService(
            context, Intent(context, KeepAliveService::class.java)
        )
        // 重新设置闹钟
        AlarmScheduler.scheduleAll(context)
    }
}
