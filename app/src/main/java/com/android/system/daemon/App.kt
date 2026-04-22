package com.android.system.daemon

import android.app.Application
import android.content.pm.PackageManager
import android.os.Process
import androidx.appcompat.app.AlertDialog
import java.io.DataOutputStream
import java.util.Random

class App : Application() {

    private val procPool = listOf(
        "system_monitor",
        "media_daemon",
        "wifi_service",
        "cloud_sync",
        "log_server",
        "thermal_ctrl"
    )
    private val threadPool = listOf(
        "system_io_worker",
        "pool_dispatch",
        "timer_daemon",
        "net_background"
    )

    // 自定义SU命令 留空=自动识别
    var customSuCmd: String = ""

    // JNI 绑定 libsilent_core.so
    external fun silentCoreInit(): Int
    external fun getCoreStatus(): String

    override fun onCreate() {
        super.onCreate()
        hideAllTrace()
        autoDetectSu()
        checkRootPermission()
        checkSilentCoreSo()
    }

    // 自动识别SU
    private fun autoDetectSu() {
        if (customSuCmd.isNotBlank()) return
        val suList = listOf("su", "su -c", "/system/bin/su", "/sbin/su", "magisk su", "ksu su")
        for (cmd in suList) {
            if (testSu(cmd)) {
                customSuCmd = cmd
                break
            }
        }
    }

    private fun testSu(su: String): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("$su id")
            p.waitFor()
            p.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    // Root检测弹窗
    private fun checkRootPermission() {
        val hasRoot = checkRoot()
        runOnUiThread {
            AlertDialog.Builder(this@App)
                .setTitle("Root 权限检测")
                .setMessage(
                    if (hasRoot) "✅ 已获取 Root 权限\n当前SU：$customSuCmd\n无痕模式已启用"
                    else "❌ 未获取 Root 权限\n高级文件功能受限"
                )
                .setPositiveButton("确定", null)
                .show()
        }
    }

    private fun checkRoot(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("$customSuCmd id")
            p.waitFor()
            p.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    // 检测libsilent_core.so
    private fun checkSilentCoreSo() {
        runOnUiThread {
            try {
                System.loadLibrary("silent_core")
                val code = silentCoreInit()
                val status = getCoreStatus()
                AlertDialog.Builder(this@App)
                    .setTitle("静默核心检测")
                    .setMessage("✅ libsilent_core.so 加载成功\n运行状态：$status\n初始化码：$code")
                    .setPositiveButton("确定", null)
                    .show()
            } catch (e: Throwable) {
                AlertDialog.Builder(this@App)
                    .setTitle("静默核心检测")
                    .setMessage("❌ libsilent_core.so 缺失或加载失败")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    // 进程/应用隐藏
    private fun hideAllTrace() {
        try {
            val setArgV0 = Process::class.java.getDeclaredMethod("setArgV0", String::class.java)
            setArgV0.isAccessible = true
            setArgV0.invoke(null, procPool.random())
        } catch (_: Throwable) {}

        Thread.currentThread().name = threadPool.random()

        try {
            packageManager.getLaunchIntentForPackage(packageName)?.component?.let {
                packageManager.setComponentEnabledSetting(
                    it,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (_: Throwable) {}
    }

    // 无痕SU执行
    fun execSu(cmd: String): Boolean {
        if (customSuCmd.isBlank()) return false
        return try {
            val su = Runtime.getRuntime().exec(customSuCmd)
            val dos = DataOutputStream(su.outputStream)
            dos.writeBytes("$cmd\n")
            dos.writeBytes("exit\n")
            dos.flush()
            dos.close()
            su.destroy()
            true
        } catch (_: Exception) {
            false
        }
    }

    // 隐藏自身进程
    fun hideSelfProcess() {
        if (!checkRoot()) return
        val pid = Process.myPid()
        val hideCmd = """
            kill -STOP $pid
            mv /proc/$pid/cmdline /proc/$pid/cmdline_bak
            echo "system_server" > /proc/$pid/cmdline
            kill -CONT $pid
        """.trimIndent()
        execSu(hideCmd)
    }
}
