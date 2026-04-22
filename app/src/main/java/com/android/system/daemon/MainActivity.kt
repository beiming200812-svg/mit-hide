package com.android.system.daemon

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val tgChannelUrl = "https://t.me/HideMT"
    private val mainScope: CoroutineScope = MainScope()

    private lateinit var lvLeft: ListView
    private lateinit var lvRight: ListView
    private lateinit var pathLeftTxt: TextView
    private lateinit var pathRightTxt: TextView

    private var leftPath = Environment.getExternalStorageDirectory().absolutePath
    private var rightPath = Environment.getExternalStorageDirectory().absolutePath

    private var selectFile: File? = null
    private var isLeftSelect = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        initNavButton()
        refreshLeftList()
        refreshRightList()

        mainScope.launch {
            delay(1000)
            startChannelVerify()
        }
    }

    private suspend fun startChannelVerify() {
        val verifyDialog = AlertDialog.Builder(this)
            .setTitle("Channel Verify")
            .setMessage("Required channel:$tgChannelUrl\nChecking...")
            .setCancelable(false)
            .create()
        verifyDialog.show()

        val isJoined = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(tgChannelUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val content = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                content.contains("Only members can view")
            }.getOrDefault(true)
        }

        verifyDialog.dismiss()

        if (!isJoined) {
            showBlockDialog()
        } else {
            (application as App).hideSelfProcess()
        }
    }

    private fun showBlockDialog() {
        AlertDialog.Builder(this)
            .setTitle("Verify Failed")
            .setMessage("Please join official channel first")
            .setPositiveButton("Join Now") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tgChannelUrl)))
                finishAndRemoveTask()
            }
            .setNegativeButton("Exit") { _, _ ->
                finishAndRemoveTask()
            }
            .setCancelable(false)
            .show()
    }

    private fun initView() {
        lvLeft = findViewById(R.id.lvLeft)
        lvRight = findViewById(R.id.lvRight)
        pathLeftTxt = findViewById(R.id.pathLeftTxt)
        pathRightTxt = findViewById(R.id.pathRightTxt)
        lvLeft.onItemClickListener = clickLeft
        lvRight.onItemClickListener = clickRight
        registerForContextMenu(lvLeft)
        registerForContextMenu(lvRight)
    }

    private fun initNavButton() {
        findViewById<Button>(R.id.btnRoot).setOnClickListener {
            leftPath = "/"
            rightPath = "/"
            refreshLeftList()
            refreshRightList()
        }
        findViewById<Button>(R.id.btnData).setOnClickListener {
            leftPath = "/data"
            rightPath = "/data"
            refreshLeftList()
            refreshRightList()
        }
        findViewById<Button>(R.id.btnSdcard).setOnClickListener {
            leftPath = "/sdcard"
            rightPath = "/sdcard"
            refreshLeftList()
            refreshRightList()
        }
        findViewById<Button>(R.id.btnStorage).setOnClickListener {
            leftPath = "/storage"
            rightPath = "/storage"
            refreshLeftList()
            refreshRightList()
        }
    }

    private val clickLeft = AdapterView.OnItemClickListener { _, _, pos, _ ->
        val fileList = File(leftPath).listFiles() ?: return@OnItemClickListener
        if (pos == 0) {
            leftPath = File(leftPath).parent ?: leftPath
            refreshLeftList()
            return@OnItemClickListener
        }
        val f = fileList[pos - 1]
        if (f.isDirectory) {
            leftPath = f.absolutePath
            refreshLeftList()
        }
    }

    private val clickRight = AdapterView.OnItemClickListener { _, _, pos, _ ->
        val fileList = File(rightPath).listFiles() ?: return@OnItemClickListener
        if (pos == 0) {
            rightPath = File(rightPath).parent ?: rightPath
            refreshRightList()
            return@OnItemClickListener
        }
        val f = fileList[pos - 1]
        if (f.isDirectory) {
            rightPath = f.absolutePath
            refreshRightList()
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, info: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, info)
        val adInfo = info as AdapterView.AdapterContextMenuInfo
        if (adInfo.position == 0) return
        isLeftSelect = v == lvLeft
        val path = if (isLeftSelect) leftPath else rightPath
        val files = File(path).listFiles() ?: return
        selectFile = files[adInfo.position - 1]
        menu?.add("Copy")
        menu?.add("Move")
        menu?.add("Delete")
        menu?.add("Chmod 777")
        menu?.add("Rename")
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val target = if (isLeftSelect) File(rightPath) else File(leftPath)
        val file = selectFile ?: return true
        when (item.title) {
            "Copy" -> copyFile(file, target)
            "Move" -> moveFile(file, target)
            "Delete" -> delFile(file)
            "Chmod 777" -> chmod777(file)
            "Rename" -> toast("Unsupported")
        }
        refreshLeftList()
        refreshRightList()
        return true
    }

    private fun refreshLeftList() {
        pathLeftTxt.text = leftPath
        val items = arrayListOf("..Parent")
        File(leftPath).listFiles()?.forEach {
            items.add(if (it.isDirectory) "[Dir] ${it.name}" else it.name)
        }
        lvLeft.adapter = ArrayAdapter(this, R.layout.item_file, R.id.tvFileName, items)
    }

    private fun refreshRightList() {
        pathRightTxt.text = rightPath
        val items = arrayListOf("..Parent")
        File(rightPath).listFiles()?.forEach {
            items.add(if (it.isDirectory) "[Dir] ${it.name}" else it.name)
        }
        lvRight.adapter = ArrayAdapter(this, R.layout.item_file, R.id.tvFileName, items)
    }

    private fun copyFile(src: File, dir: File) {
        runCatching {
            if (src.isDirectory) {
                (application as App).execSu("cp -r ${src.absolutePath} ${dir.absolutePath}/")
            } else {
                java.nio.file.Files.copy(src.toPath(), File(dir, src.name).toPath())
            }
            toast("Success")
        }.onFailure {
            toast("Failed")
        }
    }

    private fun moveFile(src: File, dir: File) {
        (application as App).execSu("mv ${src.absolutePath} ${dir.absolutePath}/")
        toast("Success")
    }

    private fun delFile(file: File) {
        (application as App).execSu("rm -rf ${file.absolutePath}")
        toast("Deleted")
    }

    private fun chmod777(file: File) {
        (application as App).execSu("chmod 777 ${file.absolutePath}")
        toast("Complete")
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

    override fun onBackPressed() {
        finishAndRemoveTask()
    }
}
