package com.android.system.daemon

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE or
            WindowManager.LayoutParams.FLAG_EXCLUDE_FROM_RECENTS,
            WindowManager.LayoutParams.FLAG_SECURE or
            WindowManager.LayoutParams.FLAG_EXCLUDE_FROM_RECENTS
        )

        setContentView(R.layout.activity_main)
        startService(Intent(this, QuietService::class.java))
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
