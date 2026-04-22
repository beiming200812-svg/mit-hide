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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private val tgChannelUrl = "https://t.me/HideMT"
    private lateinit var leftListView: ListView
    private lateinit var rightListView: ListView
    private lateinit var leftPathText: TextView
    private lateinit var rightPathText: TextView

    private var leftCurrentPath: String = Environment.getExternalStorageDirectory().absolutePath
    private var rightCurrentPath: String = Environment.getExternalStorageDirectory().absolutePath
    private var showHiddenFiles: Boolean = false

    private var leftSelectedFile: File? = null
    private var rightSelectedFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        initGlobalRootMount()
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

    private fun initGlobalRootMount() {
        val app = application as App
        app.execSu("mount -o rw,remount /")
        app.execSu("mount -o rw,remount /system")
        app.execSu("mount -o rw,remount /vendor")
    }

    private val leftItemClick = AdapterView.OnItemClickListener { _, _, position, _ ->
        val fileList = getFileList(leftCurrentPath)
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
        val fileList = getFileList(rightCurrentPath)
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

    private fun getFileList(path: String): List<File> {
        val dir = File(path)
        val list = mutableListOf<File>()
        dir.listFiles()?.forEach {
            if (!it.name.startsWith(".") || showHiddenFiles) {
                list.add(it)
            }
        }
        list.sortWith(compareBy({!it.isDirectory}, {it.name.lowercase()}))
        return list
    }

    private fun refreshLeftFileList() {
        leftPathText.text = leftCurrentPath
        val files = getFileList(leftCurrentPath)
        val displayList = files.map {
            if (it.isDirectory) "[Dir] ${it.name}" else it.name
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
        leftListView.adapter = adapter
    }

    private fun refreshRightFileList() {
        rightPathText.text = rightCurrentPath
        val files = getFileList(rightCurrentPath)
        val displayList = files.map {
            if (it.isDirectory) "[Dir] ${it.name}" else it.name
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
        menu?.add("Chmod")
        menu?.add("Properties")
        menu?.add("Toggle Hidden Files")
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.title) {
            "Toggle Hidden Files" -> {
                showHiddenFiles = !showHiddenFiles
                refreshLeftFileList()
                refreshRightFileList()
            }
            "Copy" -> copySelectedFile()
            "Move" -> moveSelectedFile()
            "Delete" -> deleteSelectedFile()
            "Rename" -> renameSelectedFile()
            "Chmod" -> showChmodDialog()
            "Properties" -> showFileProperties()
        }
        return true
    }

    private fun copySelectedFile() {
        val source = leftSelectedFile ?: return
        val targetDir = File(rightCurrentPath)
        Thread {
            runCatching {
                copyRecursive(source, File(targetDir, source.name))
                runOnUiThread { refreshRightFileList(); toast("Copy Success") }
            }.onFailure { runOnUiThread { toast("Copy Failed") } }
        }.start()
    }

    private fun moveSelectedFile() {
        val source = leftSelectedFile ?: return
        val target = File(rightCurrentPath, source.name)
        val app = application as App
        app.execSu("mv ${source.absolutePath} ${target.absolutePath}")
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
        val newName = System.currentTimeMillis().toString()
        val app = application as App
        app.execSu("mv ${target.absolutePath} ${target.parent}/$newName")
        refreshLeftFileList()
    }

    private fun showChmodDialog() {
        val target = leftSelectedFile ?: return
        val app = application as App
        app.execSu("chmod 777 ${target.absolutePath}")
        toast("Chmod 777 Complete")
    }

    private fun showFileProperties() {
        val target = leftSelectedFile ?: return
        val size = target.length()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(target.lastModified()))
        AlertDialog.Builder(this)
            .setTitle("File Properties")
            .setMessage("Path: ${target.absolutePath}\nSize: $size bytes\nModify Time: $time")
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
        if (leftCurrentPath != "/") {
            leftCurrentPath = File(leftCurrentPath).parent ?: "/"
            refreshLeftFileList()
        } else {
            finishAndRemoveTask()
        }
    }
}
