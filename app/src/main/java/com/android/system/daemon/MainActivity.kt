package com.android.system.daemon

import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideAppProcess()
    }

    private fun hideAppProcess() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
        } catch (_: Exception) {}
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
