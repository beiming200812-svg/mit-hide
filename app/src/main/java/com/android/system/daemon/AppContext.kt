package com.android.system.daemon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppContext {
    fun disableSelf(context: Context) {
        val component = ComponentName(context, MainActivity::class.java)
        context.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
