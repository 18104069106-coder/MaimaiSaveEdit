package com.savetool.maimai.ui

import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.GsonBuilder
import com.savetool.maimai.LogEntry
import com.savetool.maimai.logic.SaveManager
import com.savetool.maimai.models.SaveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.io.File
import android.os.Environment

@Composable
fun Screens(
    selectedTab: Int,
    saveData: com.savetool.maimai.models.SaveData?,
    logEntries: List<com.savetool.maimai.LogEntry>,
    isLocked: Boolean,
    onLoadMain: () -> Unit,
    onLoadSecondary: () -> Unit,
    onMergeDivingFish: () -> Unit,
    onTransfer: (String) -> Unit,
    onQuickInherit: (String) -> Unit,
    onDownload: (String) -> Unit,
    onJsonImported: (com.savetool.maimai.models.SaveData, String, com.google.gson.JsonObject?) -> Unit,
    onUploadRinnet: () -> Unit,
    rinnetUrl: String,
    workingDirUri: android.net.Uri?,
    onSetWorkingDir: () -> Unit,
    onLog: (String, String) -> Unit,
    isCnSaveDetected: Boolean = false,
    onAccountSelected: (com.savetool.maimai.AccountSource) -> Unit,
    currentAccountType: com.savetool.maimai.AccountSource,
    onReadNfcClick: () -> Unit,
    scannedAccessCode: String?
) {
    when (selectedTab) {
        0 -> InfoScreen(saveData, workingDirUri, onSetWorkingDir, onLoadMain, isLocked, isCnSaveDetected, onAccountSelected, currentAccountType, onJsonImported, onLog)
        1 -> QueryScreen(onJsonImported, workingDirUri, onLog)
        2 -> RinnetScreen(rinnetUrl, onJsonImported, workingDirUri, onLog)
        3 -> EditScreen(saveData, onLog, isLocked)
        4 -> MergeScreen(saveData, onLoadSecondary, onMergeDivingFish, isLocked)
        5 -> TransferScreen(onTransfer, onQuickInherit, isLocked, onReadNfcClick, scannedAccessCode)
        6 -> LogScreen(logEntries)
        7 -> DownloadScreen(saveData, onDownload, onUploadRinnet)
    }
}

@Composable
fun QueryScreen(onJsonImported: (SaveData, String, com.google.gson.JsonObject?) -> Unit, workingDirUri: android.net.Uri?, onLog: (String, String) -> Unit) {
    WebViewScreen("https://sdgb.lemonno.xyz/", onJsonImported, workingDirUri, onLog)
}

@Composable
fun RinnetScreen(url: String, onJsonImported: (SaveData, String, com.google.gson.JsonObject?) -> Unit, workingDirUri: android.net.Uri?, onLog: (String, String) -> Unit) {
    WebViewScreen(url, onJsonImported, workingDirUri, onLog)
}

@Composable
fun WebViewScreen(url: String, onJsonImported: (SaveData, String, com.google.gson.JsonObject?) -> Unit, workingDirUri: android.net.Uri?, onLog: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val gson = remember { GsonBuilder().serializeNulls().create() }
    val context = LocalContext.current
    
    var filePathCallback: ValueCallback<Array<android.net.Uri>>? by remember { mutableStateOf(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    key(url) {
        var progress by remember { mutableFloatStateOf(0f) }
        var isLoading by remember { mutableStateOf(true) }
        var webView: WebView? by remember { mutableStateOf(null) }

        class JSInterface {
            private val notificationId = 1002

            @JavascriptInterface
            fun log(msg: String) {
                onLog("JS控制台", msg)
            }

            @JavascriptInterface
            fun onProgress(received: Int, total: Int) {
                val p = if (total > 0) (received * 100 / total) else -1
                updateNotification("正在准备数据...", p, notificationId)
            }

            @JavascriptInterface
            fun onBlobData(base64Data: String, fileName: String) {
                onLog("捕获Blob", "文件名: $fileName, 大小: ${base64Data.length} chars")
                scope.launch(Dispatchers.IO) {
                    try {
                        updateNotification("正在解析存档...", 90, notificationId)
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        val jsonStr = String(bytes)
                        val jsonObject = gson.fromJson(jsonStr, com.google.gson.JsonObject::class.java)
                        
                        if (jsonObject != null && jsonObject.has("server") && jsonObject.get("server").asString == "cn") {
                            withContext(Dispatchers.Main) {
                                onJsonImported(SaveData(), fileName, jsonObject)
                                cancelNotification(notificationId)
                            }
                            return@launch
                        }

                        val data = gson.fromJson(jsonStr, SaveData::class.java)
                        
                        if (data != null && data.userData != null) {
                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                                File(downloadsDir, fileName).writeBytes(bytes)
                            }
                            withContext(Dispatchers.Main) {
                                onJsonImported(data, fileName, jsonObject)
                                cancelNotification(notificationId)
                                
                                // Save to working directory if exists
                                workingDirUri?.let { uri: android.net.Uri ->
                                    try {
                                        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                                        val file = root?.findFile(fileName) ?: root?.createFile("application/json", fileName)
                                        file?.uri?.let { fileUri: android.net.Uri ->
                                            context.contentResolver.openOutputStream(fileUri)?.use { it.write(bytes) }
                                        }
                                    } catch (e: Exception) {
                                        onLog("同步失败", e.message ?: "")
                                    }
                                }
                                
                                Toast.makeText(webView?.context, "已识别并自动加载 Blob 存档：$fileName", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            onLog("解析失败", "JSON 缺少 userData 字段")
                            cancelNotification(notificationId)
                        }
                    } catch (e: Exception) {
                        onLog("Blob 解析出错", e.message ?: "未知错误")
                        withContext(Dispatchers.Main) {
                            cancelNotification(notificationId)
                            Toast.makeText(webView?.context, "Blob 解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            private fun updateNotification(text: String, progress: Int, id: Int) {
                val context = webView?.context ?: return
                val builder = NotificationCompat.Builder(context, "DOWNLOAD_CHANNEL")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("存档下载中")
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                
                if (progress >= 0) {
                    builder.setProgress(100, progress, false)
                } else {
                    builder.setProgress(0, 0, true)
                }

                try {
                    NotificationManagerCompat.from(context).notify(id, builder.build())
                } catch (e: SecurityException) {
                    // Ignore if no permission
                }
            }

            private fun cancelNotification(id: Int) {
                val context = webView?.context ?: return
                NotificationManagerCompat.from(context).cancel(id)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                WebView(context).apply {
                    webView = this
                    addJavascriptInterface(JSInterface(), "AndroidBridge")
                    
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            val uri = request?.url
                            if (uri != null && uri.toString().startsWith("http://127.0.0.1/callback")) {
                                val code = uri.getQueryParameter("code")
                                if (code != null) {
                                    scope.launch(Dispatchers.IO) {
                                        val token = com.savetool.maimai.logic.DivingFishManager.exchangeCodeForToken(code)
                                        if (token != null) {
                                            val data = com.savetool.maimai.logic.DivingFishManager.fetchRecords(token)
                                            if (data != null) {
                                                withContext(Dispatchers.Main) {
                                                    onJsonImported(data, "diving_fish_export.json", null)
                                                    onLog("水鱼同步", "同步成功: ${data.userMusicDetailList.size} 条记录")
                                                    view?.loadUrl(url)
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "获取水鱼记录失败", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "水鱼授权失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    return true
                                }
                            }
                            return super.shouldOverrideUrlLoading(view, request)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            CookieManager.getInstance().flush()

                            // Inject JWT extractor for RinNET
                            val extractTokenJs = """
                                (function() {
                                    for (let i = 0; i < localStorage.length; i++) {
                                        let key = localStorage.key(i);
                                        let val = localStorage.getItem(key);
                                        if (val && typeof val === 'string' && val.includes('eyJhbGciOi')) {
                                            let match = val.match(/(eyJhbGciOi[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+)/);
                                            if (match) return match[1];
                                        }
                                    }
                                    return null;
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(extractTokenJs) { result ->
                                val token = result?.trim('"', '\'')
                                if (!token.isNullOrEmpty() && token != "null") {
                                    view.context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                                        .edit().putString("rinnet_jwt_token", token).apply()
                                }
                            }
                            
                            // Deep interceptor injection
                            val injection = """
                                (function() {
                                    if (window.blobInterceptorInjected) return;
                                    window.blobInterceptorInjected = true;
                                    AndroidBridge.log("Blob拦截器已注入");

                                    window.handleBlobUrl = function(url, fileName) {
                                        AndroidBridge.log("尝试处理Blob URL: " + url);
                                        var xhr = new XMLHttpRequest();
                                        xhr.open('GET', url, true);
                                        xhr.responseType = 'blob';
                                        xhr.onprogress = function(e) {
                                            if (e.lengthComputable) AndroidBridge.onProgress(e.loaded, e.total);
                                        };
                                        xhr.onload = function() {
                                            if (this.status == 200) {
                                                var blob = this.response;
                                                var reader = new FileReader();
                                                reader.onloadend = function() {
                                                    var base64data = reader.result.split(',')[1];
                                                    AndroidBridge.onBlobData(base64data, fileName || 'save.json');
                                                };
                                                reader.readAsDataURL(this.response);
                                            }
                                        };
                                        xhr.onerror = function(e) { AndroidBridge.log("XHR错误"); };
                                        xhr.send();
                                    };

                                    window.addEventListener('click', function(e) {
                                        var t = e.target;
                                        while (t && t.tagName !== 'A') t = t.parentNode;
                                        if (t && t.href && t.href.indexOf('blob:') === 0) {
                                            AndroidBridge.log("拦截到Blob链接点击");
                                            window.handleBlobUrl(t.href, t.download);
                                        }
                                    }, true);

                                    var orgClick = HTMLAnchorElement.prototype.click;
                                    HTMLAnchorElement.prototype.click = function() {
                                        if (this.href && this.href.indexOf('blob:') === 0) {
                                            AndroidBridge.log("拦截到程序化Blob点击");
                                            window.handleBlobUrl(this.href, this.download);
                                        }
                                        orgClick.apply(this, arguments);
                                    };
                                })();
                            """.trimIndent()
                            evaluateJavascript(injection, null)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallbackInner: ValueCallback<Array<android.net.Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            filePathCallback = filePathCallbackInner
                            fileChooserLauncher.launch(arrayOf("*/*"))
                            return true
                        }
                    }
                    
                    setDownloadListener { downloadUrl, _, contentDisposition, mimetype, _ ->
                        val fileName = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                        val notificationId = 1001
                        
                        onLog("下载请求", "URL: $downloadUrl")

                        if (fileName.endsWith(".json") || downloadUrl.startsWith("blob:")) {
                            if (downloadUrl.startsWith("blob:")) {
                                evaluateJavascript("window.handleBlobUrl('$downloadUrl', '$fileName')", null)
                            } else {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val context = webView?.context ?: return@launch
                                        val builder = NotificationCompat.Builder(context, "DOWNLOAD_CHANNEL")
                                            .setSmallIcon(android.R.drawable.stat_sys_download)
                                            .setContentTitle("正在下载存档")
                                            .setContentText(fileName)
                                            .setPriority(NotificationCompat.PRIORITY_LOW)
                                            .setOngoing(true)
                                            .setProgress(100, 0, true)
                                        
                                        val notificationManager = NotificationManagerCompat.from(context)
                                        fun safeNotify(builder: NotificationCompat.Builder) {
                                            try {
                                                notificationManager.notify(notificationId, builder.build())
                                            } catch (e: SecurityException) { }
                                        }

                                        safeNotify(builder)

                                        val urlConn = URL(downloadUrl).openConnection()
                                        val contentLength = urlConn.contentLength
                                        val inputStream = urlConn.getInputStream()
                                        val buffer = ByteArray(4096)
                                        var bytesRead: Int
                                        var totalBytesRead = 0
                                        val output = java.io.ByteArrayOutputStream()

                                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                            totalBytesRead += bytesRead
                                            if (contentLength > 0) {
                                                val progress = (totalBytesRead * 100 / contentLength)
                                                builder.setProgress(100, progress, false)
                                                safeNotify(builder)
                                            }
                                        }
                                        
                                        val jsonStr = output.toString("UTF-8")
                                        val jsonObject = gson.fromJson(jsonStr, com.google.gson.JsonObject::class.java)

                                        if (jsonObject?.get("server")?.asString == "cn") {
                                            withContext(Dispatchers.Main) {
                                                onJsonImported(SaveData(), fileName, jsonObject)
                                                try { notificationManager.cancel(notificationId) } catch (e: Exception) {}
                                            }
                                            return@launch
                                        }

                                        val data = gson.fromJson(jsonStr, SaveData::class.java)
                                        
                                        if (data != null && data.userData != null) {
                                            withContext(Dispatchers.Main) {
                                                onJsonImported(data, fileName, jsonObject)
                                                
                                                // Save to working directory if exists
                                                workingDirUri?.let { uri: android.net.Uri ->
                                                    try {
                                                        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                                                        val file = root?.findFile(fileName) ?: root?.createFile("application/json", fileName)
                                                        file?.uri?.let { fileUri: android.net.Uri ->
                                                            context.contentResolver.openOutputStream(fileUri)?.use { it.write(jsonStr.toByteArray()) }
                                                        }
                                                    } catch (e: Exception) {
                                                        onLog("同步失败", e.message ?: "")
                                                    }
                                                }
                                                
                                                try { notificationManager.cancel(notificationId) } catch (e: Exception) {}
                                                Toast.makeText(context, "已识别并自动加载存档：$fileName", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            onLog("解析失败", "JSON 缺少 userData 字段")
                                            try { notificationManager.cancel(notificationId) } catch (e: Exception) {}
                                        }
                                    } catch (e: Exception) {
                                        onLog("下载出错", e.message ?: "未知错误")
                                        withContext(Dispatchers.Main) {
                                            try { NotificationManagerCompat.from(webView!!.context).cancel(notificationId) } catch (ex: Exception) {}
                                            Toast.makeText(webView?.context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    saveData: com.savetool.maimai.models.SaveData?,
    workingDirUri: android.net.Uri?,
    onSetWorkingDir: () -> Unit,
    onLoadMain: () -> Unit,
    isLocked: Boolean,
    isCnSaveDetected: Boolean,
    onAccountSelected: (com.savetool.maimai.AccountSource) -> Unit,
    currentAccountType: com.savetool.maimai.AccountSource,
    onJsonImported: (com.savetool.maimai.models.SaveData, String, com.google.gson.JsonObject?) -> Unit,
    onLog: (String, String) -> Unit
) {
    var showAccountMenu by remember { mutableStateOf(false) }
    var showAuthWebView by remember { mutableStateOf(false) }
    val authUrl = remember { com.savetool.maimai.logic.DivingFishManager.generateAuthUrl() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard("设置") {
                Text("工作目录：${workingDirUri?.path ?: "未设置"}")
                Button(onClick = onSetWorkingDir, modifier = Modifier.fillMaxWidth(), enabled = !isLocked) {
                    Text(if (workingDirUri == null) "设置工作目录" else "更改工作目录")
                }
                if (workingDirUri == null) {
                    Text("提示：设置工作目录后，存档将自动同步到该文件夹，方便上传。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            
            Button(onClick = onLoadMain, modifier = Modifier.fillMaxWidth(), enabled = !isLocked) {
                Text("从本地载入存档")
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showAccountMenu = true }
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("当前账号源: ${currentAccountType.displayName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("▾", fontSize = 24.sp)
                        }
                        
                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false }
                        ) {
                            com.savetool.maimai.AccountSource.values().forEach { source ->
                                DropdownMenuItem(
                                    text = { Text(source.displayName) },
                                    onClick = {
                                        onAccountSelected(source)
                                        showAccountMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (currentAccountType == com.savetool.maimai.AccountSource.DIVING_FISH) {
                SectionCard("水鱼账号服务") {
                    Text(
                        if (saveData != null) "已连接水鱼查分器账号" else "尚未同步水鱼查分器账号",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { showAuthWebView = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (saveData != null) "重新登录 / 同步数据" else "登录 / 同步水鱼账号")
                    }
                }
            }

            if (saveData == null && !isCnSaveDetected) {
                if (currentAccountType != com.savetool.maimai.AccountSource.DIVING_FISH) {
                    Text("尚未载入存档", modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            } else {
                val dataSource = if (isCnSaveDetected) "国服 (CN)" else when (saveData?.gameId) {
                    "SDEZ" -> "私服 (SDEZ)"
                    else -> "水鱼查分器"
                }

                if (isCnSaveDetected) {
                    SectionCard("基础信息") {
                        InfoRow("数据源", dataSource)
                        InfoRow("用户名", "-")
                        InfoRow("AccessCode", "-")
                        InfoRow("Rating", "-")
                        InfoRow("Old Rating", "-")
                        InfoRow("段位", "-")
                    }
                } else if (currentAccountType == com.savetool.maimai.AccountSource.DIVING_FISH) {
                    saveData?.userData?.let { user ->
                        SectionCard("基础信息") {
                            InfoRow("数据源", "水鱼 (查分器)")
                            InfoRow("用户名", user.userName)
                            InfoRow("Rating", user.playerRating.toString())
                            InfoRow("歌曲成绩数", saveData.userMusicDetailList.size.toString())
                        }
                    }
                } else saveData?.userData?.let { user ->
                    SectionCard("基础信息") {
                        InfoRow("数据源", dataSource)
                        InfoRow("用户名", user.userName)
                        InfoRow("AccessCode", user.accessCode)
                        InfoRow("Rating", user.playerRating.toString())
                        InfoRow("Old Rating", user.playerOldRating.toString())
                        InfoRow("段位", user.classRank.toString())
                    }

                    SectionCard("当前装备") {
                        InfoRow("背景ID", user.frameId.toString())
                        InfoRow("头像ID", user.iconId.toString())
                        InfoRow("姓名框ID", user.plateId.toString())
                        InfoRow("搭档ID", user.partnerId.toString())
                        InfoRow("角色槽", user.charaSlot?.toString() ?: "")
                        InfoRow("锁定的角色槽", user.charaLockSlot?.toString() ?: "")
                    }

                    SectionCard("游玩记录") {
                        InfoRow("游玩次数", user.playCount.toString())
                        InfoRow("最新ROM版本", user.lastRomVersion)
                        InfoRow("最新数据版本", user.lastDataVersion)
                        InfoRow("上次登录时间", user.lastLoginDate)
                        InfoRow("上次游玩时间", user.lastPlayDate)
                        InfoRow("上次游玩地点", user.lastPlaceName)
                        InfoRow("上次游玩机台序列号", user.lastClientId)
                    }

                    SectionCard("统计信息") {
                        InfoRow("歌曲成绩", saveData.userMusicDetailList.size.toString())
                        InfoRow("收藏品", saveData.userItemList.size.toString())
                        InfoRow("角色", saveData.userCharacterList.size.toString())
                        InfoRow("地图", saveData.userMapList.size.toString())
                        InfoRow("KALEIDXSCOPE", saveData.userKaleidxScopeList.size.toString())
                    }
                }
            }
        }

        if (showAuthWebView) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("登录水鱼查分器", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                            IconButton(onClick = { showAuthWebView = false }) {
                                Text("✕", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    WebViewScreen(
                        url = authUrl,
                        onJsonImported = { data, name, json ->
                            onJsonImported(data, name, json)
                            showAuthWebView = false
                        },
                        workingDirUri = null,
                        onLog = onLog
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(saveData: SaveData?, onLog: (String, String) -> Unit, isLocked: Boolean) {
    if (saveData == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先载入存档")
        }
        return
    }

    if (isLocked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("存档已转换或合并，操作已锁定。\n请重新打开应用以开始新任务。", color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    val itemKinds = listOf(
        "1" to "姓名框 (Plate)",
        "2" to "称号 (Trophy)",
        "3" to "头像 (Icon)",
        "6" to "未知类型 (6)",
        "7" to "未知类型 (7)",
        "10" to "搭档 (Partner)",
        "11" to "背景 (Frame)"
    )

    val gateNames = mapOf(
        "1" to "青の扉", "2" to "白の扉", "3" to "紫の扉", "4" to "黄の扉", "5" to "黒の扉",
        "6" to "赤の扉", "7" to "プリズムタワー", "8" to "KALEIDXSCOPE", "9" to "希望の扉", "10" to "KALEIDXSCOPE(里)"
    )

    val difficulties = listOf(
        "0" to "Basic",
        "1" to "Advance",
        "2" to "Expert",
        "3" to "Master",
        "4" to "ReMaster"
    )

    val dxPresets = listOf(
        "-1" to "保持原样",
        "-2" to "设为 0 分",
        "6" to "理论DX分",
        "0" to "星级 0",
        "1" to "星级 1",
        "2" to "星级 2",
        "3" to "星级 3",
        "4" to "星级 4",
        "5" to "星级 5",
        "custom" to "自定义数值"
    )

    val comboStatuses = listOf(
        "-1" to "保持原样",
        "0" to "无",
        "1" to "FullCombo",
        "2" to "FullCombo+",
        "3" to "AllPerfect",
        "4" to "AllPerfect+"
    )

    val syncStatuses = listOf(
        "-1" to "保持原样",
        "0" to "无",
        "1" to "FullSync",
        "2" to "FullSync+",
        "3" to "FullSyncDX",
        "4" to "FullSyncDX+",
        "5" to "Sync"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Username
        var name by remember { mutableStateOf(saveData.userData?.userName ?: "") }
        EditCard("修改用户名") {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("新用户名") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                SaveManager.updateUsername(saveData, name)
                onLog("修改名称", name)
            }) { Text("应用") }
        }

        // 2. Rating & Play Count
        var rating by remember { mutableStateOf(saveData.userData?.playerRating?.toString() ?: "0") }
        var playCount by remember { mutableStateOf(saveData.userData?.playCount?.toString() ?: "0") }
        EditCardColumn("基础数值") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = rating, onValueChange = { rating = it }, label = { Text("Rating") }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    SaveManager.updateRating(saveData, rating.toIntOrNull() ?: 0)
                    onLog("修改Rating", rating)
                }) { Text("应用") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = playCount, onValueChange = { playCount = it }, label = { Text("游玩次数") }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    SaveManager.updatePlayCount(saveData, playCount.toIntOrNull() ?: 0)
                    onLog("修改游玩次数", playCount)
                }) { Text("应用") }
            }
        }

        // 3. Version
        var romVersion by remember { mutableStateOf(saveData.userData?.lastRomVersion ?: "") }
        var dataVersion by remember { mutableStateOf(saveData.userData?.lastDataVersion ?: "") }
        EditCardColumn("修改版本号") {
            OutlinedTextField(value = romVersion, onValueChange = { romVersion = it }, label = { Text("ROM 版本") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = dataVersion, onValueChange = { dataVersion = it }, label = { Text("DATA 版本") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                SaveManager.updateVersions(saveData, romVersion, dataVersion)
                onLog("修改版本号", "ROM: $romVersion, DATA: $dataVersion")
            }, modifier = Modifier.fillMaxWidth()) { Text("应用") }
        }

        // 4. Item Management
        var itemKind by remember { mutableStateOf("1") }
        var itemId by remember { mutableStateOf("") }
        var expandedItemKind by remember { mutableStateOf(false) }

        EditCardColumn("物品管理") {
            ExposedDropdownMenuBox(
                expanded = expandedItemKind,
                onExpandedChange = { expandedItemKind = !expandedItemKind }
            ) {
                OutlinedTextField(
                    value = itemKinds.find { it.first == itemKind }?.second ?: "自定义 ($itemKind)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("物品类型 (Kind)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedItemKind) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedItemKind,
                    onDismissRequest = { expandedItemKind = false }
                ) {
                    itemKinds.forEach { (kind, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                itemKind = kind
                                expandedItemKind = false
                            }
                        )
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = itemId, onValueChange = { itemId = it }, label = { Text("物品 ID") }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    val k = itemKind.toIntOrNull() ?: 0
                    val i = itemId.toIntOrNull() ?: 0
                    SaveManager.unlockItem(saveData, k, i)
                    onLog("解锁物品", "Kind: $k, ID: $i")
                }) { Text("解锁") }
                Button(onClick = {
                    val k = itemKind.toIntOrNull() ?: 0
                    val i = itemId.toIntOrNull() ?: 0
                    SaveManager.lockItem(saveData, k, i)
                    onLog("锁定物品", "Kind: $k, ID: $i")
                }) { Text("锁定") }
            }
        }

        // 5. KALEIDXSCOPE
        var gateId by remember { mutableStateOf("1") }
        var expandedGate by remember { mutableStateOf(false) }
        EditCardColumn("KALEIDXSCOPE") {
            ExposedDropdownMenuBox(
                expanded = expandedGate,
                onExpandedChange = { expandedGate = !expandedGate }
            ) {
                OutlinedTextField(
                    value = "${gateId}. ${gateNames[gateId] ?: ""}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("选择门 (Gate)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGate) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedGate,
                    onDismissRequest = { expandedGate = false }
                ) {
                    (1..10).forEach { i ->
                        val idStr = i.toString()
                        DropdownMenuItem(
                            text = { Text("$idStr. ${gateNames[idStr] ?: ""}") },
                            onClick = {
                                gateId = idStr
                                expandedGate = false
                            }
                        )
                    }
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    val g = gateId.toIntOrNull() ?: 1
                    SaveManager.setKaleidxGate(saveData, g, true)
                    onLog("解锁 Gate", g.toString())
                }, modifier = Modifier.weight(1f)) { Text("解锁门") }
                Button(onClick = {
                    val g = gateId.toIntOrNull() ?: 1
                    SaveManager.setKaleidxGate(saveData, g, false)
                    onLog("锁定 Gate", g.toString())
                }, modifier = Modifier.weight(1f)) { Text("锁定门") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    val g = gateId.toIntOrNull() ?: 1
                    SaveManager.setKaleidxKey(saveData, g, true)
                    onLog("获取 Key", g.toString())
                }, modifier = Modifier.weight(1f)) { Text("拿钥匙") }
                Button(onClick = {
                    val g = gateId.toIntOrNull() ?: 1
                    SaveManager.setKaleidxKey(saveData, g, false)
                    onLog("移除 Key", g.toString())
                }, modifier = Modifier.weight(1f)) { Text("弃钥匙") }
            }
        }

        // 6. Map Management
        var mapIdInput by remember { mutableStateOf("") }
        EditCardColumn("地图管理") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = mapIdInput, onValueChange = { mapIdInput = it }, label = { Text("地图 ID") }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    val m = mapIdInput.toIntOrNull() ?: 0
                    SaveManager.setMapStatus(saveData, m, true, true)
                    onLog("地图通关", m.toString())
                }) { Text("通关") }
                Button(onClick = {
                    val m = mapIdInput.toIntOrNull() ?: 0
                    SaveManager.setMapStatus(saveData, m, false, false)
                    onLog("地图重置", m.toString())
                }) { Text("重置") }
            }
            Button(onClick = {
                SaveManager.batchSetMapStatus(saveData, true, true)
                onLog("全地图通关", "All")
            }, modifier = Modifier.fillMaxWidth()) { Text("标记所有地图通关") }
        }

        // 7. Equipment
        var frameId by remember { mutableStateOf(saveData.userData?.frameId?.toString() ?: "") }
        var iconId by remember { mutableStateOf(saveData.userData?.iconId?.toString() ?: "") }
        var plateId by remember { mutableStateOf(saveData.userData?.plateId?.toString() ?: "") }
        var pntrId by remember { mutableStateOf(saveData.userData?.partnerId?.toString() ?: "") }
        var nplateId by remember { mutableStateOf(saveData.userData?.nameplateId?.toString() ?: "") }
        var titleId by remember { mutableStateOf(saveData.userData?.titleId?.toString() ?: "") }

        EditCardColumn("装扮修改") {
            EquipRow("背景 (Frame)", frameId, { frameId = it }) { SaveManager.updateEquip(saveData, "frameId", it.toIntOrNull() ?: 0); onLog("修改背景", it) }
            EquipRow("头像 (Icon)", iconId, { iconId = it }) { SaveManager.updateEquip(saveData, "iconId", it.toIntOrNull() ?: 0); onLog("修改头像", it) }
            EquipRow("姓名框 (Plate)", plateId, { plateId = it }) { SaveManager.updateEquip(saveData, "plateId", it.toIntOrNull() ?: 0); onLog("修改姓名框", it) }
            EquipRow("搭档 (Partner)", pntrId, { pntrId = it }) { SaveManager.updateEquip(saveData, "partnerId", it.toIntOrNull() ?: 0); onLog("修改搭档", it) }
            EquipRow("名称框 (Nameplate)", nplateId, { nplateId = it }) { SaveManager.updateEquip(saveData, "nameplateId", it.toIntOrNull() ?: 0); onLog("修改名称框", it) }
            EquipRow("称号 (Title)", titleId, { titleId = it }) { SaveManager.updateEquip(saveData, "titleId", it.toIntOrNull() ?: 0); onLog("修改称号", it) }
        }

        // 8. Character/Partner
        var charaId by remember { mutableStateOf("") }
        var charaLevel by remember { mutableStateOf("") }
        EditCard("角色等级") {
            OutlinedTextField(value = charaId, onValueChange = { charaId = it }, label = { Text("角色 ID") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = charaLevel, onValueChange = { charaLevel = it }, label = { Text("等级") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                val id = charaId.toIntOrNull() ?: 0
                val lv = charaLevel.toIntOrNull() ?: 1
                SaveManager.updateCharacterLevel(saveData, id, lv)
                onLog("修改角色等级", "ID: $id, Level: $lv")
            }) { Text("应用") }
        }

        var pId by remember { mutableStateOf("") }
        var pLevel by remember { mutableStateOf("") }
        EditCard("搭档亲密度") {
            OutlinedTextField(value = pId, onValueChange = { pId = it }, label = { Text("搭档 ID") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = pLevel, onValueChange = { pLevel = it }, label = { Text("等级") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                val id = pId.toIntOrNull() ?: 0
                val lv = pLevel.toIntOrNull() ?: 1
                SaveManager.updatePartnerIntimacy(saveData, id, lv)
                onLog("修改搭档等级", "ID: $id, Level: $lv")
            }) { Text("应用") }
        }

        // 9. Class Rank & maimile
        var cRank by remember { mutableStateOf(saveData.userData?.classRank?.toString() ?: "0") }
        var maimile by remember { mutableStateOf(saveData.userData?.point?.toString() ?: "0") }
        EditCardColumn("段位与 maimile") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = cRank, onValueChange = { cRank = it }, label = { Text("段位 ID") }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    val r = cRank.toIntOrNull() ?: 0
                    SaveManager.updateClassRank(saveData, r)
                    onLog("修改段位", r.toString())
                }) { Text("应用") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = maimile, onValueChange = { maimile = it }, label = { Text("maimile") }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    val p = maimile.toIntOrNull() ?: 0
                    SaveManager.updatePoint(saveData, p)
                    onLog("修改 maimile", p.toString())
                }) { Text("应用") }
            }
        }

        // 10. Music Score (Overhauled based on Section 5)
        var mMusicId by remember { mutableStateOf("") }
        var mLevel by remember { mutableStateOf("3") } // Default Master
        var mAchievementText by remember { mutableStateOf("100.4999") }
        var mDxOption by remember { mutableStateOf("-1") } // Default Keep Existing
        var mDxCustomText by remember { mutableStateOf("") }
        var mComboOption by remember { mutableStateOf("-1") }
        var mSyncOption by remember { mutableStateOf("-1") }
        
        var mError by remember { mutableStateOf("") }
        var mSuccess by remember { mutableStateOf("") }

        var expLevel by remember { mutableStateOf(false) }
        var expDx by remember { mutableStateOf(false) }
        var expCombo by remember { mutableStateOf(false) }
        var expSync by remember { mutableStateOf(false) }

        EditCardColumn("修改歌曲成绩") {
            Text("已有该难度成绩会被覆盖，没有则新建。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = mMusicId, onValueChange = { mMusicId = it; mError = "" }, label = { Text("乐曲 ID") }, modifier = Modifier.weight(1f))
                
                ExposedDropdownMenuBox(expanded = expLevel, onExpandedChange = { expLevel = !expLevel }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = difficulties.find { it.first == mLevel }?.second ?: "",
                        onValueChange = {}, readOnly = true, label = { Text("难度") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expLevel) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expLevel, onDismissRequest = { expLevel = false }) {
                        difficulties.forEach { (v, l) ->
                            DropdownMenuItem(text = { Text(l) }, onClick = { mLevel = v; expLevel = false })
                        }
                    }
                }
            }

            OutlinedTextField(
                value = mAchievementText, onValueChange = { mAchievementText = it; mError = "" },
                label = { Text("达成率 (如 100.4999 或 -1)") },
                placeholder = { Text("-1 保持原样 / -2 设为 0") },
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(expanded = expDx, onExpandedChange = { expDx = !expDx }) {
                OutlinedTextField(
                    value = dxPresets.find { it.first == mDxOption }?.second ?: "",
                    onValueChange = {}, readOnly = true, label = { Text("DX 分数") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expDx) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expDx, onDismissRequest = { expDx = false }) {
                    dxPresets.forEach { (v, l) ->
                        DropdownMenuItem(text = { Text(l) }, onClick = { mDxOption = v; expDx = false })
                    }
                }
            }
            if (mDxOption == "custom") {
                OutlinedTextField(value = mDxCustomText, onValueChange = { mDxCustomText = it; mError = "" }, label = { Text("自定义 DX 分数") }, modifier = Modifier.fillMaxWidth())
            }

            ExposedDropdownMenuBox(expanded = expCombo, onExpandedChange = { expCombo = !expCombo }) {
                OutlinedTextField(
                    value = comboStatuses.find { it.first == mComboOption }?.second ?: "",
                    onValueChange = {}, readOnly = true, label = { Text("全连状态") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expCombo) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expCombo, onDismissRequest = { expCombo = false }) {
                    comboStatuses.forEach { (v, l) ->
                        DropdownMenuItem(text = { Text(l) }, onClick = { mComboOption = v; expCombo = false })
                    }
                }
            }

            ExposedDropdownMenuBox(expanded = expSync, onExpandedChange = { expSync = !expSync }) {
                OutlinedTextField(
                    value = syncStatuses.find { it.first == mSyncOption }?.second ?: "",
                    onValueChange = {}, readOnly = true, label = { Text("同步状态") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expSync) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expSync, onDismissRequest = { expSync = false }) {
                    syncStatuses.forEach { (v, l) ->
                        DropdownMenuItem(text = { Text(l) }, onClick = { mSyncOption = v; expSync = false })
                    }
                }
            }

            if (mError.isNotEmpty()) Text(mError, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            if (mSuccess.isNotEmpty()) Text(mSuccess, color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)

            Button(onClick = {
                val musicId = mMusicId.toIntOrNull()
                if (musicId == null) { mError = "请输入有效的乐曲 ID"; return@Button }
                
                val achievementParsed = mAchievementText.toDoubleOrNull()
                if (achievementParsed == null) { mError = "请输入有效的达成率"; return@Button }
                
                val achievement = if (achievementParsed < 0) Math.round(achievementParsed).toInt() else Math.round(achievementParsed * 10000).toInt()
                
                val dxScore = if (mDxOption == "custom") mDxCustomText.toIntOrNull() ?: 0 else mDxOption.toInt()
                val combo = mComboOption.toInt()
                val sync = mSyncOption.toInt()

                SaveManager.updateMusicScore(saveData, musicId, mLevel.toInt(), achievement, dxScore, combo, sync)
                mSuccess = "musicId=$musicId (${difficulties.find { it.first == mLevel }?.second}) 成绩已修改"
                onLog("修改成绩", "ID:$musicId, Lv:$mLevel, Ach:$achievement")
            }, modifier = Modifier.fillMaxWidth()) { Text("应用") }
        }
    }
}

@Composable
fun MergeScreen(saveData: SaveData?, onLoadSecondary: () -> Unit, onMergeDivingFish: () -> Unit, isLocked: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("合并说明", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("• 歌曲成绩：按达成率/DX分整条取优（不相加游玩次数）", style = MaterialTheme.typography.bodyMedium)
                Text("• 收藏品：合并两边集合并取最大库存", style = MaterialTheme.typography.bodyMedium)
                Text("• KALEIDXSCOPE：标志取或、次数累加、最高成绩取优", style = MaterialTheme.typography.bodyMedium)
                Text("• 地图：标志取或、锁状态取与、距离取最大值", style = MaterialTheme.typography.bodyMedium)
                Text("• 主存档信息（用户名/装备/Rating等）保持不变", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }

        Text("提示：主/副存档均需为私服格式。若为国服原始存档，请先在『转移』页进行转换。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        Button(onClick = onLoadSecondary, enabled = saveData != null && !isLocked, modifier = Modifier.fillMaxWidth()) {
            Text("选择本地副存档合并")
        }
        
        Spacer(Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = onMergeDivingFish, 
            enabled = saveData != null && !isLocked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("从水鱼导入歌曲数据")
        }
        
        if (saveData == null) Text("请先载入主存档", color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        if (isLocked) Text("操作已锁定", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun TransferScreen(
    onTransfer: (String) -> Unit, 
    onQuickInherit: (String) -> Unit, 
    isLocked: Boolean,
    onReadNfcClick: () -> Unit,
    scannedAccessCode: String?
) {
    var accessCode by remember { mutableStateOf("") }
    
    LaunchedEffect(scannedAccessCode) {
        if (!scannedAccessCode.isNullOrBlank()) {
            accessCode = scannedAccessCode
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("把国服/官方存档转换为私服导入格式。")
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onReadNfcClick) {
                Text("读取 AIC 卡片")
            }
        }
        
        OutlinedTextField(
            value = accessCode,
            onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 20) accessCode = it },
            label = { Text("通行证卡号 (accessCode)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLocked,
            placeholder = { Text("请输入 20 位数字卡号") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        )
        Button(
            onClick = { onTransfer(accessCode) }, 
            modifier = Modifier.fillMaxWidth(), 
            enabled = !isLocked && accessCode.length == 20
        ) {
            Text("选择源存档文件")
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text("快速引继：自动合并私服与国服数据", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(
            onClick = { onQuickInherit(accessCode) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLocked && accessCode.length == 20
        ) {
            Text("开始快速引继")
        }
        
        if (isLocked) Text("操作已锁定", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun LogScreen(logEntries: List<LogEntry>) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(logEntries) { log ->
            ListItem(
                headlineContent = { Text(log.action) },
                supportingContent = { Text(log.detail) },
                trailingContent = { Text(log.time, fontSize = 12.sp, color = Color.Gray) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun DownloadScreen(saveData: SaveData?, onDownload: (String) -> Unit, onUploadRinnet: () -> Unit) {
    var fileName by remember { mutableStateOf("save_migrated.json") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(value = fileName, onValueChange = { fileName = it }, label = { Text("文件名") })
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onDownload(fileName) }, enabled = saveData != null, modifier = Modifier.fillMaxWidth()) {
            Text("下载存档")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onUploadRinnet, enabled = saveData != null, modifier = Modifier.fillMaxWidth()) {
            Text("上传至 RinNET")
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun EditCard(title: String, content: @Composable RowScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun EditCardColumn(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun EquipRow(label: String, value: String, onValueChange: (String) -> Unit, onApply: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Button(onClick = { onApply(value) }) { Text("应用") }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
