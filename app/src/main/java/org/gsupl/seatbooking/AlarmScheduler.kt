package org.gsupl.seatbooking

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.*

/**
 * 闹钟调度器：用 AlarmManager.setExactAndAllowWhileIdle 精确设置每天 7:59 和 16:59
 * 同时 WorkManager 做兜底（国产ROM可能杀 Alarm）。
 */
object AlarmScheduler {

    const val EXTRA_IS_AFTERNOON = "is_afternoon"
    private const val REQ_MORNING = 10001
    private const val REQ_AFTERNOON = 10002

    /** 调度两个闹钟 → 今天/明天的 目标时间 */
    fun scheduleAll(context: Context) {
        Prefs.appendLog("⏰ 重新设置闹钟…")
        if (Prefs.autoMorning) {
            val (h, m) = Prefs.morningTriggerHm
            schedule(context, REQ_MORNING, h, m, isAfternoon = false)
        } else {
            cancel(context, REQ_MORNING)
            Prefs.appendLog("   ⏰ 上午场自动预约已关闭")
        }

        if (Prefs.autoAfternoon) {
            val (h, m) = Prefs.afternoonTriggerHm
            schedule(context, REQ_AFTERNOON, h, m, isAfternoon = true)
        } else {
            cancel(context, REQ_AFTERNOON)
            Prefs.appendLog("   ⏰ 下午场自动预约已关闭")
        }

        // 启动 WorkManager 兜底：每15分钟检查一次闹钟是否还在
        AlarmGuardWorker.enqueue(context)
    }

    /**
     * 安排从现在起，下一次到达 hh:mm 的闹钟。
     */
    private fun schedule(context: Context, requestCode: Int, hour: Int, minute: Int, isAfternoon: Boolean) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val pi = makePendingIntent(context, requestCode, isAfternoon, update = true)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            }
            val pretty = android.text.format.DateFormat.format("MM-dd HH:mm", target)
            Prefs.appendLog("   ⏰ 下一次【${if (isAfternoon) "下午场" else "上午场"}】闹钟：$pretty")
        } catch (se: SecurityException) {
            // 没拿到精确闹钟权限（Android 12+ 可能），退化为 set
            Prefs.appendLog("   ⚠️ 精确闹钟被系统拒绝，使用普通闹钟: ${se.message}")
            am.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
        }
    }

    private fun cancel(context: Context, requestCode: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val isAfternoon = requestCode == REQ_AFTERNOON
        val pi = makePendingIntent(context, requestCode, isAfternoon, update = false)
        am.cancel(pi)
        pi.cancel()
    }

    private fun makePendingIntent(
        ctx: Context,
        requestCode: Int,
        isAfternoon: Boolean,
        update: Boolean
    ): PendingIntent {
        val i = Intent(ctx, BookingAlarmReceiver::class.java).apply {
            putExtra(EXTRA_IS_AFTERNOON, isAfternoon)
            action = "org.gsupl.seatbooking.TRIGGER_$requestCode"
        }
        val flags = if (update) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(ctx, requestCode, i, flags)
    }

    /** 执行完一次后，重新调度下一天的同一时间（保证每天都跑） */
    fun rescheduleAfterRun(context: Context, isAfternoon: Boolean) {
        scheduleAll(context)
    }
}
