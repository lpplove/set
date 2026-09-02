package org.gsupl.seatbooking

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

/**
 * 本地配置存储（封装 SharedPreferences）
 */
object Prefs {

    private const val NAME = "seat_booking_prefs"

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    // ---- 预约参数 ----
    var username: String
        get() = sp.getString("username", "202308090146") ?: "202308090146"
        set(v) = sp.edit().putString("username", v).apply()

    /** 密码明文，保存在手机本地 */
    var passwordPlain: String
        get() = sp.getString("password_plain", "") ?: ""
        set(v) = sp.edit().putString("password_plain", v).apply()

    /** 和原脚本一致：传 base64 编码后的密码给接口 */
    val passwordBase64: String
        get() = try {
            Base64.encodeToString(passwordPlain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }

    var roomNo: String
        get() = sp.getString("room_no", "26") ?: "26"
        set(v) = sp.edit().putString("room_no", v).apply()

    var tableNo: String
        get() = sp.getString("table_no", "2-265") ?: "2-265"
        set(v) = sp.edit().putString("table_no", v).apply()

    // ---- 定时开关 ----
    var autoMorning: Boolean
        get() = sp.getBoolean("auto_morning", true)
        set(v) = sp.edit().putBoolean("auto_morning", v).apply()

    var autoAfternoon: Boolean
        get() = sp.getBoolean("auto_afternoon", true)
        set(v) = sp.edit().putBoolean("auto_afternoon", v).apply()

    /** 每天 7:59 上午场开始抢号 时:分 */
    var morningTriggerHm: Pair<Int, Int>
        get() {
            val s = sp.getString("morning_trigger", "7:59") ?: "7:59"
            val p = s.split(":")
            return (p.getOrNull(0)?.toIntOrNull() ?: 7) to (p.getOrNull(1)?.toIntOrNull() ?: 59)
        }
        set(v) = sp.edit().putString("morning_trigger", "${v.first}:${v.second}").apply()

    /** 每天 16:59 下午场开始抢号 */
    var afternoonTriggerHm: Pair<Int, Int>
        get() {
            val s = sp.getString("afternoon_trigger", "16:59") ?: "16:59"
            val p = s.split(":")
            return (p.getOrNull(0)?.toIntOrNull() ?: 16) to (p.getOrNull(1)?.toIntOrNull() ?: 59)
        }
        set(v) = sp.edit().putString("afternoon_trigger", "${v.first}:${v.second}").apply()

    // ---- 日志（环形 300 行） ----
    private const val MAX_LOG = 300
    fun appendLog(line: String) {
        val ts = android.text.format.DateFormat.format("MM-dd HH:mm:ss", System.currentTimeMillis())
        val current = logs
        val next = (current + "[$ts] $line\n").lines().filter { it.isNotBlank() }
        val trimmed = if (next.size > MAX_LOG) next.takeLast(MAX_LOG) else next
        sp.edit().putString("logs", trimmed.joinToString("\n")).apply()
    }

    var logs: String
        get() = sp.getString("logs", "") ?: ""
        set(v) = sp.edit().putString("logs", v).apply()

    fun clearLogs() = sp.edit().remove("logs").apply()

    // ---- 最近一次状态（给通知栏展示） ----
    var lastMorningStatus: String
        get() = sp.getString("last_morning", "未执行") ?: "未执行"
        set(v) = sp.edit().putString("last_morning", v).apply()

    var lastAfternoonStatus: String
        get() = sp.getString("last_afternoon", "未执行") ?: "未执行"
        set(v) = sp.edit().putString("last_afternoon", v).apply()
}
