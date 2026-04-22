package com.android.system.daemon

import android.app.Service
import android.os.IBinder

class QuietService : Service() {
    override fun onDestroy() {
        stopSelf()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

