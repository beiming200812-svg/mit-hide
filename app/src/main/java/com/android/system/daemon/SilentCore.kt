package com.android.system.daemon

object SilentCore {
    init {
        System.loadLibrary("silent_core")
    }

    external fun hideBase()
    external fun hideDeep()
    external fun randomFakeProcess()
    external fun randomThreadName()
    external fun blockSystemTrace()
    external fun hideModule()
    external fun antiFreeze()
    external fun hideUninstallTrace()
    external fun startDualGuard()
    external fun silentSu(cmd: String): String
}
