package com.android.system.daemon

object SuShell {
    fun exec(cmd: String): String {
        return try {
            SilentCore.silentSu("$cmd > /dev/null 2>&1")
        } catch (e: Exception) {
            ""
        }
    }

    fun checkRoot(): Boolean {
        return exec("id -u").contains("0")
    }
}
