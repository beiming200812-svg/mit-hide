package com.android.system.daemon

import java.io.BufferedReader
import java.io.InputStreamReader

object SuShell {

    // 执行 Root 命令
    fun execRootCmd(cmd: String): String {
        return try {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readText()
            reader.close()
            process.waitFor()
            process.destroy()
            result.trim()
        } catch (e: Exception) {
            ""
        }
    }

    // 检测是否有 Root 权限
    fun isRootAvailable(): Boolean {
        return execRootCmd("id").contains("uid=0")
    }
}
