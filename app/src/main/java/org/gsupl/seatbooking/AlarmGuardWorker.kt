package org.gsupl.seatbooking

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager 兜底：每 15 分钟检查一次 "闹钟 + 保活服务" 还在不在，
 * 不在就补回来。国产ROM杀得特别狠时这一层特别有用。
 */
class AlarmGuardWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try {
            KeepAliveService.start(ctx)
            AlarmScheduler.scheduleAll(ctx)
        } catch (_: Exception) { }
        return Result.success()
    }

    companion object {
        private const val NAME = "seat_booking_alarm_guard"

        fun enqueue(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<AlarmGuardWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            try {
                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    req
                )
            } catch (_: Exception) { }
        }
    }
}
