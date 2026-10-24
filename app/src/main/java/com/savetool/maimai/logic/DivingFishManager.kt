package com.savetool.maimai.logic

import android.util.Base64
import com.google.gson.Gson
import com.savetool.maimai.models.MusicDetail
import com.savetool.maimai.models.SaveData
import com.savetool.maimai.models.UserData
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

object DivingFishManager {
    private const val CLIENT_ID = "5d452725eace3da3dc2c75fce0dfff54"
    private const val REDIRECT_URI = "http://127.0.0.1/callback"
    private val client = OkHttpClient()
    private val gson = Gson()

    private var codeVerifier: String? = null

    fun generateAuthUrl(): String {
        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier!!)
        
        return "https://auth.diving-fish.com/oauth/authorize" +
                "?response_type=code" +
                "&client_id=$CLIENT_ID" +
                "&redirect_uri=$REDIRECT_URI" +
                "&scope=prober.records.read" +
                "&code_challenge=$codeChallenge" +
                "&code_challenge_method=S256"
    }

    suspend fun exchangeCodeForToken(code: String): String? {
        val verifier = codeVerifier ?: return null
        
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("code_verifier", verifier)
            .build()

        val request = Request.Builder()
            .url("https://auth.diving-fish.com/oauth/token")
            .post(formBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                val json = gson.fromJson(body, Map::class.java)
                json["access_token"] as? String
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchRecords(accessToken: String): SaveData? {
        val request = Request.Builder()
            .url("https://www.diving-fish.com/api/maimaidxprober/player/records")
            .header("Authorization", "Bearer $accessToken")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                parseRecordsToSaveData(body)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseRecordsToSaveData(json: String?): SaveData? {
        if (json == null) return null
        
        val root = gson.fromJson(json, Map::class.java)
        val nickname = root["nickname"] as? String ?: "Unknown"
        val rating = (root["rating"] as? Double)?.toInt() ?: 0
        val records = root["records"] as? List<Map<String, Any>> ?: emptyList()
        
        val musicDetails = records.map { rec ->
            MusicDetail(
                musicId = (rec["music_id"] as? Double)?.toInt() ?: 0,
                level = (rec["level_index"] as? Double)?.toInt() ?: 0,
                playCount = 1, // Prober records don't have playCount, default 1
                achievement = ((rec["achievements"] as? Double)?.times(10000))?.toInt() ?: 0,
                comboStatus = mapCombo(rec["fc"] as? String),
                syncStatus = mapSync(rec["fs"] as? String),
                deluxscoreMax = (rec["dxScore"] as? Double)?.toInt() ?: 0,
                scoreRank = calculateScoreRank(rec["achievements"] as? Double ?: 0.0),
                extNum1 = 0
            )
        }.toMutableList()

        return SaveData(
            gameId = "SDEZ",
            userData = UserData(
                userName = nickname,
                playerRating = rating
            ),
            userMusicDetailList = musicDetails
        )
    }

    private fun mapCombo(fc: String?): Int {
        return when (fc) {
            "fc" -> 1
            "fcp" -> 2
            "ap" -> 3
            "app" -> 4
            else -> 0
        }
    }

    private fun mapSync(fs: String?): Int {
        return when (fs) {
            "fs" -> 1
            "fsp" -> 2
            "fsd" -> 3
            "fsdp" -> 4
            "sync" -> 5
            else -> 0
        }
    }

    private fun calculateScoreRank(achievement: Double): Int {
        return when {
            achievement >= 100.5 -> 14
            achievement >= 100.0 -> 13
            achievement >= 99.5 -> 12
            achievement >= 99.0 -> 11
            achievement >= 98.0 -> 10
            achievement >= 97.0 -> 9
            achievement >= 94.0 -> 8
            achievement >= 90.0 -> 7
            achievement >= 80.0 -> 6
            achievement >= 75.0 -> 5
            achievement >= 70.0 -> 4
            achievement >= 60.0 -> 3
            achievement >= 50.0 -> 2
            achievement >= 40.0 -> 1
            else -> 0
        }
    }

    private fun generateCodeVerifier(): String {
        val sr = SecureRandom()
        val code = ByteArray(32)
        sr.nextBytes(code)
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes, 0, bytes.size)
        val digest = md.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
