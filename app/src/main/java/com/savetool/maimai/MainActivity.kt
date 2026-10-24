package com.savetool.maimai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.savetool.maimai.logic.SaveManager
import com.savetool.maimai.models.SaveData
import com.savetool.maimai.ui.Screens
import com.savetool.maimai.ui.theme.MaiSaveToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter

import android.nfc.NfcAdapter
import android.nfc.Tag
import com.savetool.maimai.logic.AimeNfcReader

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    var onNfcScanned: ((String) -> Unit)? = null
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestNotificationPermission()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        enableEdgeToEdge()
        setContent {
            MaiSaveToolTheme {
                MainApp(this)
            }
        }
    }

    fun enableNfcReader() {
        val flags = NfcAdapter.FLAG_READER_NFC_A or 
                    NfcAdapter.FLAG_READER_NFC_F or 
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        nfcAdapter?.enableReaderMode(this, this, flags, null)
    }

    fun disableNfcReader() {
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        tag?.let {
            val accessCode = AimeNfcReader.readAccessCode(it)
            if (accessCode != null) {
                runOnUiThread {
                    onNfcScanned?.invoke(accessCode)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        disableNfcReader()
    }

    private fun createNotificationChannel() {
        val name = "存档下载"
        val descriptionText = "显示存档下载进度"
        val importance = android.app.NotificationManager.IMPORTANCE_LOW
        val channel = android.app.NotificationChannel("DOWNLOAD_CHANNEL", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: android.app.NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}

enum class AccountSource(val displayName: String) {
    LOCAL("本地导入"),
    RINNET("RinNET"),
    DIVING_FISH("水鱼 (查分器)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(activity: MainActivity) {
    val context = LocalContext.current
    val gson = remember { GsonBuilder().serializeNulls().create() }
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var isLocked by remember { mutableStateOf(false) }
    
    // Account Management
    var currentAccountType by remember { mutableStateOf(AccountSource.LOCAL) }
    var localSave by remember { mutableStateOf<SaveData?>(null) }
    var rinnetSave by remember { mutableStateOf<SaveData?>(prefs.getString("rinnet_cache", null)?.let { gson.fromJson(it, SaveData::class.java) }) }
    var divingFishSave by remember { mutableStateOf<SaveData?>(prefs.getString("fish_cache", null)?.let { gson.fromJson(it, SaveData::class.java) }) }
    var isDivingFishAuthorized by remember { mutableStateOf(divingFishSave != null) }
    
    val currentSave = when (currentAccountType) {
        AccountSource.LOCAL -> localSave
        AccountSource.RINNET -> rinnetSave
        AccountSource.DIVING_FISH -> divingFishSave
    }

    val tabs = listOf("信息", "查询", "RinNET", "修改", "合并", "转移", "日志", "下载")
    var isCnSaveDetected by remember { mutableStateOf(false) }
    var isQuickInheritMode by remember { mutableStateOf(false) }
    var quickInheritStep by remember { mutableIntStateOf(0) }
    var jpSaveData by remember { mutableStateOf<SaveData?>(null) }
    
    var logEntries by remember { mutableStateOf(listOf<LogEntry>()) }
    var outFileName by remember { mutableStateOf("save_migrated.json") }
    var rinnetUrl by remember { mutableStateOf("https://portal.naominet.live/dashboard") }
    
    var workingDirUri by remember { mutableStateOf(prefs.getString("working_dir", null)?.let { Uri.parse(it) }) }

    var scannedAccessCode by remember { mutableStateOf<String?>(null) }
    var isWaitingForNfc by remember { mutableStateOf(false) }

    if (isWaitingForNfc) {
        AlertDialog(
            onDismissRequest = {
                isWaitingForNfc = false
                activity.disableNfcReader()
            },
            title = { Text("NFC 扫描") },
            text = { Text("请将 AIC 卡放至手机的 NFC 识别区域") },
            confirmButton = {
                TextButton(onClick = {
                    isWaitingForNfc = false
                    activity.disableNfcReader()
                }) {
                    Text("取消")
                }
            }
        )
    }

    LaunchedEffect(isWaitingForNfc) {
        if (isWaitingForNfc) {
            activity.onNfcScanned = { code ->
                scannedAccessCode = code
                isWaitingForNfc = false
                activity.disableNfcReader()
                android.widget.Toast.makeText(context, "扫描成功！", android.widget.Toast.LENGTH_SHORT).show()
            }
            activity.enableNfcReader()
        } else {
            activity.onNfcScanned = null
        }
    }

    // Clear upload temp directory and working directory on startup
    LaunchedEffect(Unit) {
        // External working directory (SAF)
        workingDirUri?.let { uri ->
            try {
                withContext(Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(context, uri)
                    if (root != null && root.isDirectory) {
                        root.listFiles().forEach { file ->
                            if (file.name == "rinnet_upload.json" || file.name == "upload_save.json") {
                                file.delete()
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {
            }
        }
    }

    fun addLog(action: String, detail: String = "") {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logEntries = listOf(LogEntry(time, action, detail)) + logEntries
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            workingDirUri = it
            prefs.edit().putString("working_dir", it.toString()).apply()
            addLog("工作目录", "已设置为: ${it.path}")
        }
    }

    // Main file loader
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val text = inputStream?.bufferedReader()?.use { it.readText() }
                val jsonObject = gson.fromJson(text, JsonObject::class.java)
                
                if (jsonObject?.get("server")?.asString == "cn") {
                    isCnSaveDetected = true
                    localSave = null
                    addLog("载入提示", "检测到国服存档，请前往'转移'页处理")
                    android.widget.Toast.makeText(context, "检测到国服存档，请在'转移存档'处进行转移", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    val data = gson.fromJson(text, SaveData::class.java)
                    if (data?.userData != null) {
                        localSave = data
                        currentAccountType = AccountSource.LOCAL
                        isCnSaveDetected = false
                        val summary = "用户: ${data.userData?.userName}, Rating: ${data.userData?.playerRating}, 歌曲: ${data.userMusicDetailList.size}, 物品: ${data.userItemList.size}"
                        addLog("载入存档", "${it.lastPathSegment}\n$summary")
                    } else {
                        addLog("载入失败", "存档缺少 userData，且非国服原始格式")
                    }
                }
            } catch (e: Exception) {
                addLog("载入出错", e.message ?: "未知错误")
            }
        }
    }

    // Secondary file loader for Merge
    val secondaryFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val text = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
                val rawJsonObject = gson.fromJson(text, JsonObject::class.java)

                if (rawJsonObject != null) {
                    try {
                        SaveManager.requireTargetFormat(rawJsonObject, "副存档")
                    } catch (ex: IllegalArgumentException) {
                        addLog("合并拒绝", ex.message ?: "解析出错")
                        android.widget.Toast.makeText(context, ex.message, android.widget.Toast.LENGTH_LONG).show()
                        return@rememberLauncherForActivityResult
                    }
                }

                val main = currentSave
                if (main == null) {
                    addLog("合并拒绝", "没有可用的主存档")
                    android.widget.Toast.makeText(context, "请先在'信息'页载入主存档", android.widget.Toast.LENGTH_LONG).show()
                    return@rememberLauncherForActivityResult
                }

                val subSave = gson.fromJson(text, SaveData::class.java)
                val oldMusicCount = main.userMusicDetailList.size
                val oldItemCount = main.userItemList.size

                SaveManager.merge(main, subSave)

                val newMusicCount = main.userMusicDetailList.size
                val newItemCount = main.userItemList.size
                addLog(
                    "合并存档",
                    "与 ${it.lastPathSegment} 合并完成\n" +
                    "新增乐曲成绩: ${newMusicCount - oldMusicCount} (总计: $newMusicCount)\n" +
                    "新增收藏品: ${newItemCount - oldItemCount} (总计: $newItemCount)"
                )
                android.widget.Toast.makeText(context, "存档合并成功！", android.widget.Toast.LENGTH_SHORT).show()
                isLocked = true
                selectedTab = 7
            } catch (e: Exception) {
                addLog("合并出错", e.message ?: "未知错误")
                android.widget.Toast.makeText(context, "合并出错: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // Transfer file loader
    var transferAccessCode by remember { mutableStateOf("") }
    val transferFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val reader = InputStreamReader(inputStream)
                val src = gson.fromJson(reader, JsonObject::class.java)
                val result = SaveManager.migrate(src, transferAccessCode)
                
                if (isQuickInheritMode && quickInheritStep == 2) {
                    val jp = jpSaveData
                    if (jp != null) {
                        SaveManager.merge(jp, result)
                        jp.userData?.lastDataVersion = "1.55"
                        localSave = jp
                        currentAccountType = AccountSource.LOCAL
                        
                        // Generate upload file
                        val json = gson.toJson(jp)
                        val token = prefs.getString("rinnet_jwt_token", null)
                        
                        if (token != null) {
                            scope.launch(Dispatchers.Main) {
                                try {
                                    android.widget.Toast.makeText(context, "合并完成，正在自动上传至私服...", android.widget.Toast.LENGTH_SHORT).show()
                                    val success = com.savetool.maimai.logic.RinnetManager.uploadSaveData(token, json)
                                    if (success) {
                                        addLog("快速引继", "合并完成且已自动上传至私服 (Data 1.55)")
                                        android.widget.Toast.makeText(context, "自动引继成功！", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    addLog("快速引继", "上传失败: ${e.message}")
                                    android.widget.Toast.makeText(context, "自动上传失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                                isQuickInheritMode = false
                                quickInheritStep = 0
                            }
                        } else {
                            addLog("快速引继", "合并完成，Data版本已设为 1.55 (未登录私服)")
                            android.widget.Toast.makeText(context, "合并完成，未检测到私服凭证，无法自动上传", android.widget.Toast.LENGTH_LONG).show()
                            isQuickInheritMode = false
                            quickInheritStep = 0
                        }
                    }
                } else {
                    localSave = result
                    currentAccountType = AccountSource.LOCAL
                    val summary = "用户: ${result.userData?.userName}, Rating: ${result.userData?.playerRating}, 歌曲: ${result.userMusicDetailList.size}, 物品: ${result.userItemList.size}"
                    addLog("转移成功", "源文件: ${it.lastPathSegment}\n$summary")
                    isLocked = true
                    selectedTab = 7
                }
                reader.close()
            } catch (e: Exception) {
                addLog("转移出错", e.message ?: "未知错误")
            }
        }
    }

    // File Saver
    val fileSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val outputStream = context.contentResolver.openOutputStream(it)
                val writer = OutputStreamWriter(outputStream)
                gson.toJson(currentSave, writer)
                writer.close()
                addLog("保存存档", "已导出为 ${it.lastPathSegment}")
            } catch (e: Exception) {
                addLog("保存出错", e.message ?: "未知错误")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("舞萌DX 存档工具箱") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            val visibleTabs = tabs.filter { it != "修改" || currentAccountType != AccountSource.DIVING_FISH }
            val currentVisibleTabIndex = visibleTabs.indexOf(tabs[selectedTab]).coerceAtLeast(0)

            ScrollableTabRow(selectedTabIndex = currentVisibleTabIndex) {
                visibleTabs.forEach { title ->
                    val index = tabs.indexOf(title)
                    Tab(
                        selected = selectedTab == index,
                        onClick = { 
                            if (index == 2) rinnetUrl = "https://portal.naominet.live/dashboard"
                            selectedTab = index 
                        },
                        text = { Text(title) },
                        enabled = !isLocked || index == 7 || index == 6 || index == 2
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // If account switched to Diving Fish while on Edit tab, kick back to Info
                LaunchedEffect(currentAccountType) {
                    if (currentAccountType == AccountSource.DIVING_FISH && selectedTab == 3) {
                        selectedTab = 0
                    }
                }
                
                Screens(
                    selectedTab = selectedTab,
                    saveData = currentSave,
                    logEntries = logEntries,
                    isLocked = isLocked,
                    isCnSaveDetected = isCnSaveDetected,
                    onLoadMain = { filePickerLauncher.launch("application/json") },
                    onLoadSecondary = { secondaryFilePickerLauncher.launch("application/json") },
                    onMergeDivingFish = {
                        val fish = divingFishSave
                        val current = currentSave
                        if (fish != null && current != null) {
                            val oldSize = current.userMusicDetailList.size
                            SaveManager.merge(current, fish)
                            val newSize = current.userMusicDetailList.size
                            val addedCount = newSize - oldSize
                            addLog("合并水鱼", "已从水鱼查分器同步成绩，新增: $addedCount 条记录")
                            android.widget.Toast.makeText(context, "成功合并 $addedCount 条乐曲成绩", android.widget.Toast.LENGTH_LONG).show()
                            selectedTab = 7 // Jump to Download
                        } else if (fish == null) {
                            android.widget.Toast.makeText(context, "水鱼账号未同步，请在'信息'页切换至水鱼账号源并登录同步", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    onTransfer = { code ->
                        transferAccessCode = code
                        transferFilePickerLauncher.launch("application/json")
                    },
                    onQuickInherit = { code ->
                        transferAccessCode = code
                        isQuickInheritMode = true
                        quickInheritStep = 1
                        rinnetUrl = "https://portal.naominet.live/mai2/setting"
                        selectedTab = 2
                        android.widget.Toast.makeText(context, "请在网页中下载私服存档", android.widget.Toast.LENGTH_LONG).show()
                    },
                    onDownload = { name ->
                        outFileName = name
                        fileSaveLauncher.launch(name)
                    },
                    onJsonImported = { data, fileName, rawJsonObject ->
                        val isCn = rawJsonObject?.get("server")?.asString == "cn"
                        if (isCn) {
                            isCnSaveDetected = true
                            localSave = null
                            addLog("导入提示", "捕获到国服存档: $fileName")
                            android.widget.Toast.makeText(context, "检测到国服存档，请在'转移存档'处进行转移", android.widget.Toast.LENGTH_LONG).show()
                        } else if (isQuickInheritMode && quickInheritStep == 1) {
                            jpSaveData = data
                            quickInheritStep = 2
                            addLog("快速引继", "私服存档载入成功，请选择国服存档")
                            android.widget.Toast.makeText(context, "请选择国服存档进行转换与合并", android.widget.Toast.LENGTH_LONG).show()
                            transferFilePickerLauncher.launch("application/json")
                        } else {
                            if (selectedTab == 2) {
                                rinnetSave = data
                                currentAccountType = AccountSource.RINNET
                                prefs.edit().putString("rinnet_cache", gson.toJson(data)).apply()
                            } else if (fileName == "diving_fish_export.json") {
                                divingFishSave = data
                                isDivingFishAuthorized = true
                                currentAccountType = AccountSource.DIVING_FISH
                                prefs.edit().putString("fish_cache", gson.toJson(data)).apply()
                                selectedTab = 0
                                addLog("水鱼同步", "同步成功: ${data.userMusicDetailList.size} 条成绩已载入账号槽位")
                            } else {
                                localSave = data
                                currentAccountType = AccountSource.LOCAL
                            }
                            isCnSaveDetected = false
                            val summary = "用户: ${data.userData?.userName}, Rating: ${data.userData?.playerRating}, 歌曲: ${data.userMusicDetailList.size}, 物品: ${data.userItemList.size}"
                            addLog("自动导入", "$fileName\n$summary")
                        }
                    },
                    onUploadRinnet = {
                        scope.launch(Dispatchers.Main) {
                            val token = prefs.getString("rinnet_jwt_token", null)
                            if (token == null) {
                                android.widget.Toast.makeText(context, "未获取到私服登录凭证，请先前往'私服'页面登录", android.widget.Toast.LENGTH_LONG).show()
                                selectedTab = 2 // 跳转到私服页面让用户登录
                                return@launch
                            }

                            android.widget.Toast.makeText(context, "正在上传至私服，请稍候...", android.widget.Toast.LENGTH_SHORT).show()
                            try {
                                val json = gson.toJson(currentSave)
                                val success = com.savetool.maimai.logic.RinnetManager.uploadSaveData(token, json)
                                if (success) {
                                    addLog("私服同步", "存档上传成功")
                                    android.widget.Toast.makeText(context, "上传成功！", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                addLog("私服同步失败", e.message ?: "未知错误")
                                android.widget.Toast.makeText(context, "上传失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    rinnetUrl = rinnetUrl,
                    workingDirUri = workingDirUri,
                    onSetWorkingDir = { dirPickerLauncher.launch(null) },
                    onLog = { action, detail -> addLog(action, detail) },
                    onAccountSelected = { type -> currentAccountType = type },
                    currentAccountType = currentAccountType,
                    onReadNfcClick = { isWaitingForNfc = true },
                    scannedAccessCode = scannedAccessCode
                )
            }
        }
    }
}

data class LogEntry(val time: String, val action: String, val detail: String)
