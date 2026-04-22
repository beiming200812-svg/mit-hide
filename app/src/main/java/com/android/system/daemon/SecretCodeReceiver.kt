package com.android.system.daemon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SecretCodeReceiver : BroadcastReceiver() {
    companion object {
        const val SECRET_ACTION = "android.provider.Telephony.SECRET_CODE"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        // 空实现，先保证编译通过
    }
}
