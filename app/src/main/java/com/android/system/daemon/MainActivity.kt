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
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val tgChannelUrl = "https://t.me/HideMT"

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

        Handler(Looper.getMainLooper()).postDelayed({
            showTgVerifyDialog()
        }, 800)
    }

    private fun showTgVerifyDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Channel Verify")
            .setMessage("Official channel required\n$tgChannelUrl\nChecking...")
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            var joined = false
            try {
                val conn = URL(tgChannelUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val input = conn.inputStream.bufferedReader().readLine()
                if (input.contains("Only members can view")) {
                    joined = true
                }
                conn.disconnect()
            } catch (_: Exception) {
                joined = true
            }

            runOnUiThread {
                dialog.dismiss()
                if (!joined) {
                    showNoJoinDialog()
                } else {
                    (application as App).hideSelfProcess()
                }
            }
        }.start()
    }

    private fun showNoJoinDialog() {
        AlertDialog.Builder(this)
            .setTitle("Verify Failed")
            .setMessage("Please join channel first\n$tgChannelUrl")
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
            "Rename" -> toast("Not Support")
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
        try {
            if (src.isDirectory) {
                (application as App).execSu("cp -r ${src.absolutePath} ${dir.absolutePath}/")
            } else {
                java.nio.file.Files.copy(src.toPath(), File(dir, src.name).toPath())
            }
            toast("Success")
        } catch (e: Exception) {
            toast("Failed")
        }
    }

    private fun moveFile(src: File, dir: File) {
        (application as App).execSu("mv ${src.absolutePath} ${dir.absolutePath}/")
        toast("Success")
    }

    private fun delFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Confirm")
            .setMessage(file.name)
            .setPositiveButton("OK") { _, _ ->
                (application as App).execSu("rm -rf ${file.absolutePath}")
                toast("Deleted")
            }.show()
    }

    private fun chmod777(file: File) {
        (application as App).execSu("chmod 777 ${file.absolutePath}")
        toast("Complete")
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        finishAndRemoveTask()
    }
}
