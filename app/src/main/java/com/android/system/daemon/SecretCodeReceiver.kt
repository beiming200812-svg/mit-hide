package com.android.system.daemon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SecretCodeReceiver : BroadcastReceiver() {
    companion object {
        const val WAKE_CODE = "*#9988#"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "android.provider.Telephony.SECRET_CODE") {
            val code = intent.dataString ?: ""
            if (code.contains(WAKE_CODE)) {
                val launch = Intent(context, MainActivity::class.java)
                launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context?.startActivity(launch)
            }
        }
    }
}
