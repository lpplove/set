package org.gsupl.seatbooking

import android.content.Context
import kotlinx.coroutines.*

/**
 * 预约引擎：登录 → 最多30次尝试，匹配原脚本逻辑
 */
object BookingEngine {

    private const val MAX_TRY = 30

    private val TIME_RE = Regex("""(\d{2}):(\d{2}):(\d{2})""")

    suspend fun runBooking(context: Context, isAfternoon: Boolean): Boolean = withContext(Dispatchers.IO) {
        val tag = if (isAfternoon) "下午/晚上场" else "上午场"
        log(context, "========== 开始预约【$tag】 ==========")
        log(context, "阅览室: ${Prefs.roomNo}  座位: ${Prefs.tableNo}")

        val uname = Prefs.username
        val passB64 = Prefs.passwordBase64
        if (passB64.isBlank()) {
            log(context, "❌ 未设置密码，预约终止。请先在APP里填密码。")
            updateStatus(isAfternoon, "失败: 未填密码")
            return@withContext false
        }

        // 1. 登录
        log(context, "🔐 正在登录…")
        val loginR = BookingApi.login(uname, passB64)
        if (loginR.isFailure) {
            log(context, "❌ 登录失败: ${loginR.exceptionOrNull()?.message}")
            updateStatus(isAfternoon, "失败: 登录异常")
            return@withContext false
        }
        log(context, "✅ 登录请求完成")

        // 2. 循环预约
        var success = false
        var lastMsg = ""
        repeat(MAX_TRY) { idx ->
            ensureActive()
            val result = BookingApi.book(isAfternoon, Prefs.roomNo, Prefs.tableNo)
            if (result.isSuccess) {
                val r = result.getOrThrow()
                lastMsg = r.msg
                log(context, "   第${idx + 1}次 → 返回值[${r.returnValue}] $lastMsg")
                if (r.returnValue == 0) {
                    log(context, "🎉🎉🎉 预约成功！")
                    success = true
                    updateStatus(isAfternoon, "✅ 成功")
                    return@withContext true
                }
                // 根据返回信息中的时间做智能等待（原脚本 extract_date 逻辑）
                val m = TIME_RE.find(lastMsg)
                if (m != null) {
                    val sec = m.groupValues[3].toIntOrNull() ?: 0
                    if (sec in 1..59) {
                        val wait = 60 - sec + 1
                        log(context, "   ⏳ 检测到时间 ${m.value}，等待 ${wait}s…")
                        delay(wait * 1000L)
                    } else {
                        delay(1000L)
                    }
                } else {
                    delay(1000L)
                }
            } else {
                lastMsg = result.exceptionOrNull()?.message ?: "未知错误"
                log(context, "   第${idx + 1}次 → ❌ $lastMsg")
                delay(1000L)
            }
        }

        log(context, "❌ $MAX_TRY 次尝试均未成功。最后消息: $lastMsg")
        updateStatus(isAfternoon, "失败: $lastMsg")
        false
    }

    private fun log(ctx: Context, msg: String) {
        Prefs.appendLog(msg)
        // 发送广播让UI刷新
        ctx.sendBroadcast(android.content.Intent(ACTION_LOG_UPDATED).setPackage(ctx.packageName))
        // 同时更新前台服务通知
        KeepAliveService.refreshNotification(ctx)
    }

    private fun updateStatus(isAfternoon: Boolean, s: String) {
        if (isAfternoon) Prefs.lastAfternoonStatus = s else Prefs.lastMorningStatus = s
    }

    const val ACTION_LOG_UPDATED = "org.gsupl.seatbooking.LOG_UPDATED"
}
