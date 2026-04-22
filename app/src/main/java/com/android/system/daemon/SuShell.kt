package com.android.system.daemon

import java.io.BufferedReader
import java.io.InputStreamReader

object SuShell {
    fun execOnce(cmd: String): String {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readText()
            reader.close()
            process.waitFor()
            result.trim()
        } catch (_: Exception) {
            ""
        } finally {
            process?.destroy()
            process?.destroyForcibly()
        }
    }

    fun haveRoot(): Boolean {
        return execOnce("id -u") == "0"
    }
}
