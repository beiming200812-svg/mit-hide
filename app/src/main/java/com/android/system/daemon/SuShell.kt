package com.android.system.daemon

import java.io.DataOutputStream

object SuShell {

    // 无痕Root执行，不保留su常驻进程、无痕迹
    fun execRoot(cmd: String): String {
        return try {
            val su = Runtime.getRuntime().exec("su")
            val dos = DataOutputStream(su.outputStream)

            // 执行命令 + 立刻退出，不残留su进程
            dos.writeBytes("$cmd\n")
            dos.writeBytes("exit\n")
            dos.flush()
            dos.close()

            su.waitFor()
            su.destroy()
            ""
        } catch (_: Exception) {
            ""
        }
    }

    // 检测Root
    fun hasRoot(): Boolean {
        return try {
            val test = Runtime.getRuntime().exec("su -c id")
            test.waitFor()
            test.destroy()
            true
        } catch (_: Exception) {
            false
        }
    }
}
