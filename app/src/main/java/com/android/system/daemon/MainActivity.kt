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
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

class MainActivity : Activity() {

    private val tgChannelUrl = "https://t.me/HideMT"
    private var isCheckPass = false

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

        // 启动 TG 3秒验证弹窗
        showTgVerifyDialog()
    }

    // ====================== TG 3秒验证弹窗 ======================
    private fun showTgVerifyDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("频道验证")
            .setMessage(
                "使用前必须加入官方频道\n" +
                "频道地址：\n$tgChannelUrl\n\n" +
                "正在检测加入状态(3秒)..."
            )
            .setCancelable(false)
            .create()

        dialog.show()

        // 延迟3秒后执行检测
        Handler(Looper.getMainLooper()).postDelayed({
            dialog.dismiss()
            checkTgJoinStatus()
        }, 3000)
    }

    // 检测是否加入频道
    private fun checkTgJoinStatus() {
        Thread {
            var joined = false
            try {
                val url = URL(tgChannelUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                val br = BufferedReader(InputStreamReader(conn.inputStream))
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    if (line!!.contains("Only members can view") ||
                        line!!.contains("仅成员可查看")
                    ) {
                        joined = true
                        break
                    }
                }
                br.close()
                conn.disconnect()
            } catch (e: Exception) {
                joined = false
            }

            // 切回主线程
            runOnUiThread {
                if (joined) {
                    // 验证通过 进入APP
                    startApp()
                } else {
                    // 未加入 强制退出
                    showNoJoinDialog()
                }
            }
        }.start()
    }

    // 未加入弹窗
    private fun showNoJoinDialog() {
        AlertDialog.Builder(this)
            .setTitle("验证失败")
            .setMessage("你尚未加入指定频道\n无法继续使用本软件")
            .setPositiveButton("前往加入") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tgChannelUrl))
                startActivity(intent)
                finishAndRemoveTask()
            }
            .setNegativeButton("退出应用") { _, _ ->
                finishAndRemoveTask()
            }
            .setCancelable(false)
            .show()
    }

    // 验证通过 初始化主界面
    private fun startApp() {
        setContentView(R.layout.activity_main)
        (application as App).hideSelfProcess()
        initView()
        initNavButton()
        refreshLeftList()
        refreshRightList()
    }

    // ====================== 原有全部UI与文件功能不变 ======================
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

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val info = menuInfo as AdapterView.AdapterContextMenuInfo
        val pos = info.position
        if (pos == 0) return
        isLeftSelect = v == lvLeft
        val currPath = if (isLeftSelect) leftPath else rightPath
        val files = File(currPath).listFiles() ?: return
        selectFile = files[pos - 1]
        menu?.add("复制到对面窗口")
        menu?.add("移动到对面窗口")
        menu?.add("删除")
        menu?.add("ROOT权限 777")
        menu?.add("重命名")
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val targetDir = if (isLeftSelect) File(rightPath) else File(leftPath)
        val file = selectFile ?: return true
        when (item.title) {
            "复制到对面窗口" -> copyFile(file, targetDir)
            "移动到对面窗口" -> moveFile(file, targetDir)
            "删除" -> delFile(file)
            "ROOT权限 777" -> chmod777(file)
            "重命名" -> toast("自行扩展")
        }
        refreshLeftList()
        refreshRightList()
        return super.onContextItemSelected(item)
    }

    private fun refreshLeftList() {
        pathLeftTxt.text = leftPath
        val list = arrayListOf("..上级")
        File(leftPath).listFiles()?.forEach {
            list.add(if (it.isDirectory) "[文件夹] ${it.name}" else it.name)
        }
        lvLeft.adapter = ArrayAdapter(this, R.layout.item_file, R.id.tvFileName, list)
    }

    private fun refreshRightList() {
        pathRightTxt.text = rightPath
        val list = arrayListOf("..上级")
        File(rightPath).listFiles()?.forEach {
            list.add(if (it.isDirectory) "[文件夹] ${it.name}" else it.name)
        }
        lvRight.adapter = ArrayAdapter(this, R.layout.item_file, R.id.tvFileName, list)
    }

    private fun copyFile(src: File, destDir: File) {
        try {
            if (src.isDirectory) {
                (application as App).execSu("cp -r ${src.absolutePath} ${destDir.absolutePath}/")
            } else {
                Files.copy(src.toPath(), File(destDir, src.name).toPath())
            }
            toast("复制完成")
        } catch (e: Exception) {
            toast("复制失败，需要ROOT")
        }
    }

    private fun moveFile(src: File, destDir: File) {
        (application as App).execSu("mv ${src.absolutePath} ${destDir.absolutePath}/")
        toast("移动完成")
    }

    private fun delFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage(file.name)
            .setPositiveButton("删除") { _, _ ->
                (application as App).execSu("rm -rf ${file.absolutePath}")
                toast("已删除")
            }.show()
    }

    private fun chmod777(file: File) {
        (application as App).execSu("chmod 777 ${file.absolutePath}")
        toast("已设置 777 权限")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
        finishAndRemoveTask()
    }
}

