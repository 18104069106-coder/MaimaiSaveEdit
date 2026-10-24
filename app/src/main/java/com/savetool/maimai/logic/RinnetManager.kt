package com.savetool.maimai.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object RinnetManager {
    private val client = OkHttpClient()
    private const val UPLOAD_URL = "https://portal.naominet.live/api/game/maimai2/import"

    suspend fun uploadSaveData(token: String, json: String): Boolean = withContext(Dispatchers.IO) {
        val requestBody = json.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(UPLOAD_URL)
            .addHeader("Authorization", "Bearer ${token.replace("\"", "")}")
            .addHeader("User-Agent", "MaiSaveTool-Android")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("上传失败: HTTP ${response.code} ${response.message}")
            }
            return@withContext true
        }
    }
}
