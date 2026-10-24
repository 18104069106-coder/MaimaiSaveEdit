package com.savetool.maimai.logic

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.savetool.maimai.models.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SaveManagerTest {

    private val gson: Gson = GsonBuilder().serializeNulls().create()

    @Test
    fun testHigherAchievementWins() {
        val main = SaveData(userData = UserData(userName = "MainUser", playerRating = 12000))
        main.userMusicDetailList = mutableListOf(
            MusicDetail(musicId = 1, level = 3, achievement = 900000, deluxscoreMax = 100, playCount = 5)
        )

        val sub = SaveData(userData = UserData(userName = "SubUser", playerRating = 15000))
        sub.userMusicDetailList = mutableListOf(
            MusicDetail(musicId = 1, level = 3, achievement = 950000, deluxscoreMax = 50, playCount = 9)
        )

        SaveManager.merge(main, sub)

        assertEquals(1, main.userMusicDetailList.size)
        val m = main.userMusicDetailList[0]
        assertEquals(950000, m.achievement)
        assertEquals(9, m.playCount) // Entire winning record replaces, playCount not added
        assertEquals("MainUser", main.userData?.userName)
        assertEquals(12000, main.userData?.playerRating) // Main userData preserved
    }

    @Test
    fun testFullTieKeepsPrimary() {
        val main = SaveData(userData = UserData())
        main.userMusicDetailList = mutableListOf(
            MusicDetail(musicId = 1, level = 3, achievement = 900000, deluxscoreMax = 100, playCount = 5)
        )

        val sub = SaveData(userData = UserData())
        sub.userMusicDetailList = mutableListOf(
            MusicDetail(musicId = 1, level = 3, achievement = 900000, deluxscoreMax = 100, playCount = 9)
        )

        SaveManager.merge(main, sub)

        assertEquals(1, main.userMusicDetailList.size)
        assertEquals(5, main.userMusicDetailList[0].playCount) // Keeps primary record
    }

    @Test
    fun testMapLockIsAndOthersAreOr() {
        val main = SaveData(userData = UserData())
        main.userMapList = mutableListOf(
            UserMap(mapId = 1, isLock = true, isClear = false, distance = 10)
        )

        val sub = SaveData(userData = UserData())
        sub.userMapList = mutableListOf(
            UserMap(mapId = 1, isLock = false, isClear = true, distance = 30)
        )

        SaveManager.merge(main, sub)

        assertEquals(1, main.userMapList.size)
        val m = main.userMapList[0]
        assertFalse(m.isLock) // Either unlocked -> unlocked
        assertTrue(m.isClear)
        assertEquals(30, m.distance)
    }

    @Test
    fun testKaleidxFlagsOrAndPlayCountSums() {
        val main = SaveData(userData = UserData())
        main.userKaleidxScopeList = mutableListOf(
            UserKaleidxScope(
                gateId = 1, isGateFound = false, isKeyFound = false, playCount = 1,
                bestAchievement = 800000, bestAchievementDate = "2026-08-01 09:00:00"
            )
        )

        val sub = SaveData(userData = UserData())
        sub.userKaleidxScopeList = mutableListOf(
            UserKaleidxScope(
                gateId = 1, isGateFound = true, isKeyFound = false, playCount = 2,
                bestAchievement = 900000, bestAchievementDate = "2026-08-01 10:00:00"
            )
        )

        SaveManager.merge(main, sub)

        assertEquals(1, main.userKaleidxScopeList.size)
        val g = main.userKaleidxScopeList[0]
        assertTrue(g.isGateFound)
        assertFalse(g.isKeyFound)
        assertEquals(3, g.playCount) // 1 + 2
        assertEquals(900000, g.bestAchievement)
        assertEquals("2026-08-01 10:00:00", g.bestAchievementDate)
    }

    @Test
    fun testRejectsChineseServerSave() {
        val cnJson = gson.fromJson("""{"userDetails":{}}""", JsonObject::class.java)
        val exception = assertThrows(IllegalArgumentException::class.java) {
            SaveManager.requireTargetFormat(cnJson, "副存档")
        }
        assertTrue(exception.message!!.contains("国服原始存档"))
    }

    @Test
    fun testGoldenMigratedValidation() {
        val goldenFile = File("C:/Users/Administrator/Downloads/golden_migrated_example.json")
        if (goldenFile.exists()) {
            val goldenJson = gson.fromJson(goldenFile.readText(), JsonObject::class.java)
            val errors = OutputValidator.validate(goldenJson)
            assertEquals(emptyList<String>(), errors)
        }
    }

    @Test
    fun testMigrateContractValidation() {
        val cnJson = JsonObject().apply {
            add("userDetails", JsonObject().apply {
                addProperty("userName", "TestUser")
                addProperty("friendRegistSkip", 1)
            })
            add("userScoreAll_", JsonObject().apply {
                add("userMusicList", JsonArray())
            })
            add("colls", JsonObject())
            add("map", JsonObject().apply {
                add("userMapList", JsonArray())
            })
            add("kaleidx", JsonObject().apply {
                add("userKaleidxScopeList", JsonArray())
            })
            add("userCharacter", JsonObject().apply {
                add("userCharacterList", JsonArray())
            })
            add("userIntimate", JsonObject().apply {
                add("userIntimateList", JsonArray())
            })
            add("userOption", JsonObject().apply {
                addProperty("tempoVolume", 5)
            })
        }

        val migrated = SaveManager.migrate(cnJson, "12345678901234567890", nowEpochSec = 1791340222L)
        val jsonText = gson.toJson(migrated)
        val migratedObj = gson.fromJson(jsonText, JsonObject::class.java)

        val errors = OutputValidator.validate(migratedObj)
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun testUnlockItem() {
        val save = SaveData(userData = UserData())
        SaveManager.unlockItem(save, 1, 100)
        assertEquals(1, save.userItemList.size)
        assertEquals(1, save.userItemList[0].itemKind)
        assertEquals(100, save.userItemList[0].itemId)
        assertTrue(save.userItemList[0].isValid)

        // Unlock again, should not add new entry
        SaveManager.unlockItem(save, 1, 100)
        assertEquals(1, save.userItemList.size)
    }

    @Test
    fun testLockItem() {
        val save = SaveData(userData = UserData())
        SaveManager.unlockItem(save, 1, 100)
        SaveManager.lockItem(save, 1, 100)
        assertEquals(0, save.userItemList.size)
    }

    @Test
    fun testBatchMapStatus() {
        val save = SaveData(userData = UserData())
        SaveManager.batchSetMapStatus(save, isClear = true, isComplete = true)
        
        // Check a random valid map ID from ALL_MAP_IDS
        val map1 = save.userMapList.find { it.mapId == 1 }
        assertNotNull(map1)
        assertTrue(map1?.isClear ?: false)
        assertTrue(map1?.isComplete ?: false)

        // Check the broken map ID 600005, it should NOT be in the list
        val mapBroken = save.userMapList.find { it.mapId == 600005 }
        assertNull(mapBroken)
    }

    @Test
    fun testUpdateMusicScore() {
        val save = SaveData(userData = UserData())
        SaveManager.updateMusicScore(save, 10, 3, 1000000, 2000, 3, 4)
        
        val score = save.userMusicDetailList.find { it.musicId == 10 && it.level == 3 }
        assertNotNull(score)
        assertEquals(1000000, score?.achievement)
        assertEquals(2000, score?.deluxscoreMax)
        assertEquals(3, score?.comboStatus)
        assertEquals(4, score?.syncStatus)
        assertEquals(1, score?.playCount)

        // Update existing
        SaveManager.updateMusicScore(save, 10, 3, 1010000, 2100, 4, 0)
        assertEquals(1010000, score?.achievement)
        assertEquals(2100, score?.deluxscoreMax)
        assertEquals(1, score?.playCount)
    }

    @Test
    fun testUpdateUserData() {
        val save = SaveData(userData = UserData())
        SaveManager.updateRating(save, 15000)
        assertEquals(15000, save.userData?.playerRating)

        SaveManager.updateUsername(save, "TestUser")
        assertEquals("TestUser", save.userData?.userName)

        SaveManager.updatePoint(save, 99999)
        assertEquals(99999, save.userData?.point)
    }
}
