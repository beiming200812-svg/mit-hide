package com.android.system.daemon

object SuShell {
    fun exec(cmd: String): String {
        return try {
            SilentCore.silentSu(cmd)
        } catch (_: Exception) { "" }
    }
    fun checkRoot(): Boolean = exec("id -u").contains("0")
}
