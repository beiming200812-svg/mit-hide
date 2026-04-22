package com.android.system.daemon

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class MainActivity : Activity() {

    private lateinit var filesListView: ListView
    private lateinit var pathText: TextView
    private lateinit var vMenuAnchor: View

    private var currentPath: String = "/"
    private var showHiddenFiles: Boolean = true
    private var showSystemFiles: Boolean = true
    private var selectedFile: File? = null

    private lateinit var suPathEditText: EditText
    private val REQUEST_CODE_STORAGE_PERMISSIONS = 1001

    // 全局适配参数
    private var screenScale = 1.0f
    private var textScale = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 全局分辨率、比例、DPI 自动计算
        calcScreenAdapt()

        initView()
        requestStoragePermissions()
        initFullRootAccess()
        refreshFileList()

        Handler(Looper.getMainLooper()).postDelayed({
            (application as App).hideSelfProcess()
        }, 1000)
    }

    // 核心：全局屏幕比例/分辨率/DPI 统一适配
    private fun calcScreenAdapt() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        // 基准 1080P 屏幕
        val baseW = 1080f
        val baseH = 2400f
        val scaleW = screenWidth / baseW
        val scaleH = screenHeight / baseH

        // 取最小比例，防止拉伸、溢出
        screenScale = minOf(scaleW, scaleH)
        textScale = screenScale.coerceIn(0.85f, 1.25f)
    }

    private fun initView() {
        filesListView = findViewById(R.id.lv_files)
        pathText = findViewById(R.id.tv_path)
        vMenuAnchor = findViewById(R.id.v_menu_anchor)

        // 左上角呼出菜单
        vMenuAnchor.setOnClickListener { showLeftPopupMenu() }

        // 全局文字大小适配
        applyAllTextAdapt()

        filesListView.onItemClickListener = itemClickListener
        registerForContextMenu(filesListView)
    }

    // 左上角弹出菜单
    private fun showLeftPopupMenu() {
        val popup = PopupMenu(this, vMenuAnchor)
        menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener {
            when(it.itemId){
                R.id.action_settings -> showSettingsDialog()
                R.id.action_app_list -> showInstalledAppsList()
            }
            true
        }
        popup.show()
    }

    // 全局所有文字统一动态缩放
    private fun applyAllTextAdapt() {
        val basePathTextSize = 14f
        pathText.textSize = basePathTextSize * textScale

        // ListView 全局 Item 适配
        filesListView.adapter = object : ArrayAdapter<File>(this, android.R.layout.simple_list_item_1) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val tv = view.findViewById<TextView>(android.R.id.text1)
                tv.textSize = 13f * textScale
                tv.setTextColor(Color.parseColor("#212121"))
                tv.setPadding((8 * screenScale).toInt(),
                    (6 * screenScale).toInt(),
                    (8 * screenScale).toInt(),
                    (6 * screenScale).toInt())
                return view
            }
        }
    }

    private fun requestStoragePermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CODE_STORAGE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(this, "Storage permission required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initFullRootAccess() {
        val app = application as App
        app.execSu("mount -o rw,remount /")
        app.execSu("mount -o rw,remount /system")
        app.execSu("mount -o rw,remount /data")
        app.execSu("setenforce 0")
    }

    private val itemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
        val list = getFullFileList(currentPath)
        if (position < list.size) {
            val f = list[position]
            if (f.isDirectory) {
                currentPath = f.absolutePath
                refreshFileList()
            } else {
                selectedFile = f
            }
        }
    }

    private fun getFullFileList(path: String): MutableList<File> {
        val dir = File(path)
        val list = mutableListOf<File>()
        if (!dir.exists()) return list
        dir.listFiles()?.forEach {
            if (showHiddenFiles && showSystemFiles) {
                list.add(it)
            } else if (showHiddenFiles && !it.name.startsWith(".")) {
                list.add(it)
            }
        }
        list.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ENGLISH) }))
        return list
    }

    private fun refreshFileList() {
        pathText.text = currentPath
        val data = getFullFileList(currentPath).map {
            if (it.isDirectory) "[Dir] ${it.name}" else it.name
        }
        val adapter = filesListView.adapter as ArrayAdapter<String>
        adapter.clear()
        adapter.addAll(data)
        adapter.notifyDataSetChanged()
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
                refreshFileList()
            }
            "Show System Files" -> {
                showSystemFiles = !showSystemFiles
                refreshFileList()
            }
            "Copy" -> copyFile()
            "Move" -> moveFile()
            "Delete" -> deleteFile()
            "Rename" -> renameFile()
            "Chmod 777" -> chmodFile()
            "File Info" -> showFileInfo()
        }
        return true
    }

    private fun copyFile(){}
    private fun moveFile(){}
    private fun deleteFile(){
        selectedFile?.let {
            (application as App).execSu("rm -rf ${it.absolutePath}")
            refreshFileList()
        }
    }
    private fun renameFile(){}
    private fun chmodFile(){
        selectedFile?.let {
            (application as App).execSu("chmod -R 777 ${it.absolutePath}")
        }
    }

    private fun showFileInfo() {
        val f = selectedFile ?: return
        val msg = "Path:${f.absolutePath}\nSize:${f.length()}\nTime:${
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(f.lastModified()))
        }"
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("OK",null)
            .show()
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.settings_dialog,null)
        suPathEditText = view.findViewById(R.id.et_su_path)
        suPathEditText.setText((application as App).customSuCmd)

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(view)
            .setPositiveButton("Save") {_,_ ->
                val s = suPathEditText.text.toString().trim()
                (application as App).customSuCmd = s
            }
            .setNeutralButton("Reset"){_,_ ->
                (application as App).customSuCmd = ""
            }
            .show()
    }

    private fun showInstalledAppsList() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        val names = apps.map { it.loadLabel(pm).toString() }
        AlertDialog.Builder(this)
            .setItems(names.toTypedArray(),null)
            .show()
    }

    override fun onBackPressed() {
        val parent = File(currentPath).parentFile
        if (parent != null && currentPath != "/") {
            currentPath = parent.absolutePath
            refreshFileList()
        } else {
            finish()
        }
    }
}
