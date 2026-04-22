package com.android.system.daemon

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class MainActivity : Activity() {

    private lateinit var filesListView: ListView
    private lateinit var pathText: TextView

    private var currentPath: String = "/"
    private var showHiddenFiles: Boolean = true
    private var showSystemFiles: Boolean = true
    private var selectedFile: File? = null

    private lateinit var suPathEditText: EditText
    private val REQUEST_CODE_STORAGE_PERMISSIONS = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        requestStoragePermissions()
        initFullRootAccess()
        refreshFileList()

        Handler(Looper.getMainLooper()).postDelayed({
            (application as App).hideSelfProcess()
        }, 1000)
    }

    private fun initView() {
        filesListView = findViewById(R.id.lv_files)
        pathText = findViewById(R.id.tv_path)

        filesListView.onItemClickListener = itemClickListener
        registerForContextMenu(filesListView)
    }

    private fun requestStoragePermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CODE_STORAGE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(this, "Storage permissions are required for full access", Toast.LENGTH_LONG).show()
            }
        }
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

    private val itemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
        val fileList = getFullFileList(currentPath)
        if (position < fileList.size) {
            val target = fileList[position]
            if (target.isDirectory) {
                currentPath = target.absolutePath
                refreshFileList()
            } else {
                selectedFile = target
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

    private fun refreshFileList() {
        pathText.text = currentPath
        val files = getFullFileList(currentPath)
        val displayList = files.map {
            when {
                it.isDirectory -> "[Dir] ${it.name}"
                it.isHidden -> "[Hidden] ${it.name}"
                else -> it.name
            }
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
        filesListView.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            R.id.action_app_list -> {
                showInstalledAppsList()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDialog() {
        val app = application as App
        val dialogView = layoutInflater.inflate(R.layout.settings_dialog, null)
        suPathEditText = dialogView.findViewById(R.id.et_su_path)
        suPathEditText.setText(app.customSuCmd)

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newSuPath = suPathEditText.text.toString().trim()
                if (newSuPath.isNotBlank()) {
                    app.customSuCmd = newSuPath
                    Toast.makeText(this, "SU path saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "SU path cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Reset") { _, _ ->
                app.customSuCmd = ""
                suPathEditText.setText("")
                Toast.makeText(this, "SU path reset to auto-detect", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showInstalledAppsList() {
        val packageManager = packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val appNames = apps.map { it.loadLabel(packageManager).toString() }.toMutableList()
        val appPackages = apps.map { it.packageName }.toMutableList()

        AlertDialog.Builder(this)
            .setTitle("Installed Applications")
            .setItems(appNames.toTypedArray()) { _, position ->
                val appName = appNames[position]
                val packageName = appPackages[position]
                Toast.makeText(this, "Selected: $appName\nPackage: $packageName", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Close", null)
            .show()
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
        val source = selectedFile ?: return
        val targetDir = File(currentPath)
        Thread {
            runCatching {
                copyRecursive(source, File(targetDir, source.name))
                runOnUiThread { refreshFileList() }
            }
        }.start()
    }

    private fun moveSelectedFile() {
        val source = selectedFile ?: return
        val target = File(currentPath, source.name)
        val app = application as App
        app.execSu("mv -f ${source.absolutePath} ${target.absolutePath}")
        refreshFileList()
    }

    private fun deleteSelectedFile() {
        val target = selectedFile ?: return
        val app = application as App
        app.execSu("rm -rf ${target.absolutePath}")
        refreshFileList()
    }

    private fun renameSelectedFile() {
        val target = selectedFile ?: return
        val app = application as App
        val newName = System.currentTimeMillis().toString()
        app.execSu("mv ${target.absolutePath} ${target.parent}/$newName")
        refreshFileList()
    }

    private fun chmodSelectedFile() {
        val target = selectedFile ?: return
        val app = application as App
        app.execSu("chmod -R 777 ${target.absolutePath}")
    }

    private fun showFileDetails() {
        val target = selectedFile ?: return
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

    override fun onBackPressed() {
        val parentFile = File(currentPath).parentFile
        if (parentFile != null && currentPath != "/") {
            currentPath = parentFile.absolutePath
            refreshFileList()
        } else {
            finishAndRemoveTask()
        }
    }
}

