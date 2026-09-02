package org.gsupl.seatbooking

import android.Manifest
import android.app.AlarmManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import kotlinx.coroutines.*
import org.gsupl.seatbooking.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshLogs()
            refreshStatus()
        }
    }
    private val scope = MainScope()

    // ---------- 权限请求 ----------
    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 读取配置到输入框
        binding.etUsername.setText(Prefs.username)
        binding.etPassword.setText(Prefs.passwordPlain)
        binding.etRoom.setText(Prefs.roomNo)
        binding.etSeat.setText(Prefs.tableNo)
        binding.swMorning.isChecked = Prefs.autoMorning
        binding.swAfternoon.isChecked = Prefs.autoAfternoon

        val (mh, mm) = Prefs.morningTriggerHm
        val (ah, am) = Prefs.afternoonTriggerHm
        binding.etMorningTime.setText(String.format("%02d:%02d", mh, mm))
        binding.etAfternoonTime.setText(String.format("%02d:%02d", ah, am))

        // 保存按钮
        binding.btnSave.setOnClickListener { savePrefs() }

        // 手动预约按钮
        binding.btnBookMorning.setOnClickListener {
            savePrefs()
            runManual(isAfternoon = false)
        }
        binding.btnBookAfternoon.setOnClickListener {
            savePrefs()
            runManual(isAfternoon = true)
        }

        // 清除日志
        binding.btnClearLog.setOnClickListener {
            Prefs.clearLogs()
            refreshLogs()
        }

        // 申请权限按钮
        binding.btnPermission.setOnClickListener { askAllPermissions() }

        // 重新设置闹钟
        binding.btnReschedule.setOnClickListener {
            savePrefs()
            KeepAliveService.start(this)
            AlarmScheduler.scheduleAll(this)
            Toast.makeText(this, "已重新设置闹钟（见下方日志）", Toast.LENGTH_SHORT).show()
        }

        refreshStatus()
        refreshLogs()

        // 首次启动自动请求必要权限
        askAllPermissions(forceDialog = false)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(logReceiver, IntentFilter(BookingEngine.ACTION_LOG_UPDATED))
        refreshStatus()
        refreshLogs()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(logReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ---------- 保存配置 ----------
    private fun savePrefs() {
        Prefs.username = binding.etUsername.text?.toString()?.trim().orEmpty()
        Prefs.passwordPlain = binding.etPassword.text?.toString()?.trim().orEmpty()
        Prefs.roomNo = binding.etRoom.text?.toString()?.trim().orEmpty()
        Prefs.tableNo = binding.etSeat.text?.toString()?.trim().orEmpty()
        Prefs.autoMorning = binding.swMorning.isChecked
        Prefs.autoAfternoon = binding.swAfternoon.isChecked

        // 解析时间
        parseHm(binding.etMorningTime.text?.toString())?.let { Prefs.morningTriggerHm = it }
        parseHm(binding.etAfternoonTime.text?.toString())?.let { Prefs.afternoonTriggerHm = it }

        // 重新调度
        KeepAliveService.start(this)
        AlarmScheduler.scheduleAll(this)
        AlarmGuardWorker.enqueue(this)
        KeepAliveService.refreshNotification(this)

        Toast.makeText(this, "✅ 配置已保存，闹钟已刷新", Toast.LENGTH_SHORT).show()
        refreshStatus()
        refreshLogs()
    }

    private fun parseHm(s: CharSequence?): Pair<Int, Int>? {
        val ss = s?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val p = ss.split(":", "：", ".", "-", "/")
        val h = p.getOrNull(0)?.toIntOrNull() ?: return null
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        if (h !in 0..23 || m !in 0..59) return null
        return h to m
    }

    // ---------- 手动预约 ----------
    private fun runManual(isAfternoon: Boolean) {
        if (Prefs.passwordPlain.isBlank()) {
            Toast.makeText(this, "⚠️ 请先填密码并保存", Toast.LENGTH_SHORT).show()
            return
        }
        val exec = Intent(this, BookingExecutorService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_IS_AFTERNOON, isAfternoon)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(exec)
        } else {
            startService(exec)
        }
        Toast.makeText(this, "🚀 已启动（${if (isAfternoon) "下午场" else "上午场"}）…请查看日志", Toast.LENGTH_SHORT).show()
    }

    // ---------- 状态/日志 ----------
    private fun refreshStatus() {
        val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
        binding.tvLastMorning.text = "上午最近结果：${Prefs.lastMorningStatus}"
        binding.tvLastAfternoon.text = "下午最近结果：${Prefs.lastAfternoonStatus}"

        // 显示下次闹钟：只显示"已调度"
        val autoMorning = binding.swMorning.isChecked
        val autoAfternoon = binding.swAfternoon.isChecked
        val (mh, mm) = Prefs.morningTriggerHm
        val (ah, am) = Prefs.afternoonTriggerHm
        binding.tvNextAlarm.text = buildString {
            append("自动计划：")
            append(if (autoMorning) "每天${"%02d:%02d".format(mh, mm)}抢上午场 " else "上午(关) ")
            append(if (autoAfternoon) "每天${"%02d:%02d".format(ah, am)}抢下午场" else " 下午(关)")
        }
        binding.tvNow.text = "当前手机时间：${sdf.format(Date())}"
    }

    private fun refreshLogs() {
        binding.tvLogs.text = Prefs.logs.ifBlank { "还没有日志。保存配置或点手动预约后会显示在这里。" }
        binding.scrollLogs.post { binding.scrollLogs.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    // ---------- 权限请求 ----------
    private fun askAllPermissions(forceDialog: Boolean = true) {
        // 1. 通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. 忽略电池优化
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("需要：忽略电池优化")
                .setMessage("为了让每天的定时预约不被系统杀死，请在系统弹出的页面上选择「所有应用」→ 找到本APP → 选择「不优化」→ 完成。")
                .setPositiveButton("去设置") { _, _ ->
                    try {
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:$packageName"))
                        )
                    } catch (e: Exception) {
                        Toast.makeText(this, "系统拒绝跳转：${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("取消", null)
                .also { if (forceDialog) it.show() }
        }

        // 3. 精确闹钟权限（Android 12+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                val builder = AlertDialog.Builder(this)
                    .setTitle("需要：精确闹钟权限")
                    .setMessage("安卓12+要求单独授权精确闹钟，否则定时触发会严重不准。请把本APP加入「可使用精确闹钟」的名单。")
                    .setPositiveButton("去设置") { _, _ ->
                        try {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        } catch (e: Exception) {
                            Toast.makeText(this, "跳转失败：${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("稍后再说", null)
                if (forceDialog) builder.show()
            }
        }

        // 4. 自启动引导（仅提示）
        if (forceDialog) {
            AlertDialog.Builder(this)
                .setTitle("强烈建议：允许自启动 + 锁定后台")
                .setMessage(
                    "国产ROM（小米/华为/OPPO/vivo/荣耀等）会杀死后台应用，请手动做两件事：\n" +
                            "1. 手机管家 → 应用管理 → 座位预约助手 → 允许【自启动】\n" +
                            "2. 最近任务里，把本APP【下拉/长按 → 锁定】（显示小锁头）\n\n" +
                            "完成后，定时预约的成功率会大大提高。"
                )
                .setPositiveButton("知道了", null)
                .show()
        }
    }
}
