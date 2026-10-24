package com.savetool.maimai.logic

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.savetool.maimai.models.*
import java.text.SimpleDateFormat
import java.util.*

object SaveManager {

    private val BROKEN_MAP_IDS = listOf(600005)
    private val ALL_MAP_IDS = listOf(
        1, 2, 3, 4, 5, 6, 7, 31, 32, 33, 34, 35, 36, 38, 39, 40,
        50001, 50002, 50003, 50004, 50005, 50006, 50007, 50008, 50009,
        50031, 50032, 50033, 50034, 50036, 50037, 50038, 50039,
        100001, 100003, 100004, 100005, 100006, 100007,
        100051, 100052, 100053, 100054, 100055, 100056, 100057, 100058,
        100059, 100060, 100061, 100062,
        150001, 150002, 150003, 150004, 150005, 150006,
        150051, 150052, 150053, 150054, 150055, 150056, 150057, 150059, 150060,
        200001, 200002, 200003, 200004, 200005, 200006,
        200051, 200052, 200053, 200054, 200055, 200056, 200057, 200058, 200059,
        200060, 200061, 200062, 200063,
        250001, 250002, 250003, 250004, 250005, 250006, 250007,
        250051, 250052, 250053, 250054, 250055, 250056, 250057, 250058, 250059, 250060,
        300001, 300002, 300003, 300004, 300005, 300006,
        300051, 300052, 300053, 300054, 300055, 300056, 300057, 300058, 300059, 300060, 300061,
        350001, 350003, 350004, 350005, 350006, 350007,
        350051, 350052, 350053, 350054, 350055, 350056, 350057, 350058,
        400002, 400003, 400004, 400005, 400006,
        400051, 400052, 400053, 400054, 400055, 400056, 400057, 400058, 400059, 400061, 400062, 400063,
        450001, 450002, 450003, 450004, 450005,
        450051, 450052, 450053, 450054, 450055, 450056, 450057, 450058, 450059, 450061,
        500001, 500002, 500003, 500004, 500005,
        500051, 500052, 500053, 500054, 500055, 500056, 500057, 500058, 500059, 500060,
        550001, 550002, 550003, 550004,
        550051, 550052, 550054, 550055, 550056, 550057, 550058, 550059, 550060,
        600001, 600002, 600003, 600004,
        600051, 600053, 600054, 500055, 600057, 600058, 600059, 600060, 600061,
        650001, 650002, 650003, 650005,
        650051, 650052, 650053, 650055, 650056, 650057, 650058, 650059, 650060
    )

    private val REQUIRED_SOURCE_KEYS = listOf(
        "userDetails", "userScoreAll_", "colls", "map",
        "kaleidx", "userCharacter", "userIntimate", "userOption"
    )

    private val TARGET_META = mapOf(
        "eventWatchedDate" to "2026-08-17 13:23:21.0",
        "lastGameId" to "SDGA",
        "lastRomVersion" to "1.60.00",
        "lastDataVersion" to "1.60.00",
        "lastLoginDate" to "2026-08-17 13:23:21.0",
        "lastPlayDate" to "2026-08-17 13:43:25.0",
        "lastPlayCredit" to 2,
        "lastPlayMode" to 0,
        "lastPlaceId" to 25,
        "lastPlaceName" to "誰もいないセカイ",
        "lastAllNetId" to 0,
        "lastRegionId" to 4,
        "lastRegionName" to "湖",
        "lastClientId" to "A69E01A8888",
        "lastCountryCode" to "JP",
        "lastSelectEMoney" to 0,
        "lastSelectTicket" to 0,
        "lastSelectCourse" to 0,
        "lastCountCourse" to 0,
        "firstGameId" to "SDGA",
        "firstRomVersion" to "1.60.00",
        "firstDataVersion" to "1.60.00",
        "firstPlayDate" to "2026-08-17 00:47:19.0",
        "compatibleCmVersion" to "1.60.00",
        "dailyBonusDate" to "1970-01-01 09:00:00.0",
        "dailyCourseBonusDate" to "1970-01-01 09:00:00.0",
        "lastPairLoginDate" to "1970-01-01 09:00:00.0",
        "lastTrialPlayDate" to "1970-01-01 09:00:00.0"
    )

    fun requireTargetFormat(save: SaveData, name: String = "存档") {
        if (save.userData == null) {
            throw IllegalArgumentException("$name 不是私服格式（缺少 userData）。")
        }
    }

    fun requireTargetFormat(saveObj: JsonObject, name: String = "存档") {
        val hasUserData = saveObj.has("userData") && !saveObj.get("userData").isJsonNull
        val hasUserDetails = saveObj.has("userDetails") && !saveObj.get("userDetails").isJsonNull
        if (!hasUserData) {
            if (hasUserDetails) {
                throw IllegalArgumentException("$name 是国服原始存档，请先在『转移』页转换。")
            } else {
                throw IllegalArgumentException("$name 不是私服格式（缺少 userData / userMusicDetailList）。")
            }
        }
    }

    fun migrate(
        src: JsonObject,
        accessCode: String,
        nowEpochSec: Long = System.currentTimeMillis() / 1000
    ): SaveData {
        val missingKeys = REQUIRED_SOURCE_KEYS.filter { !src.has(it) || src.get(it).isJsonNull }
        require(missingKeys.isEmpty()) { "存档缺少必要字段: ${missingKeys.joinToString()}" }
        require(accessCode.isNotBlank()) { "通行证卡号不能为空（必须由玩家手动输入，不能自动生成）" }

        // 1. UserMusicDetailList
        val musicList = mutableListOf<MusicDetail>()
        val scoreAll = src.getAsJsonObject("userScoreAll_")
        scoreAll?.getAsJsonArray("userMusicList")?.forEach { musicEntry ->
            musicEntry.asJsonObject.getAsJsonArray("userMusicDetailList")?.forEach { detail ->
                val dm = detail.asJsonObject
                // Strictly Whitelisted 9 fields
                musicList.add(MusicDetail(
                    musicId = dm.getIntSafe("musicId"),
                    level = dm.getIntSafe("level"),
                    playCount = dm.getIntSafe("playCount"),
                    achievement = dm.getIntSafe("achievement"),
                    comboStatus = dm.getIntSafe("comboStatus"),
                    syncStatus = dm.getIntSafe("syncStatus"),
                    deluxscoreMax = dm.getIntSafe("deluxscoreMax"),
                    scoreRank = dm.getIntSafe("scoreRank"),
                    extNum1 = dm.getIntSafe("extNum1")
                ))
            }
        }

        // 2. UserData
        val d = src.getAsJsonObject("userDetails") ?: JsonObject()
        val userData = UserData().apply {
            userName = d.getStringSafe("userName")
            friendCode = ""
            isNetMember = d.getIntSafe("isNetMember")
            nameplateId = d.getIntSafe("nameplateId")
            iconId = d.getIntSafe("iconId")
            trophyId = d.getIntSafe("trophyId")
            plateId = d.getIntSafe("plateId")
            titleId = d.getIntSafe("titleId")
            partnerId = d.getIntSafe("partnerId")
            frameId = d.getIntSafe("frameId")
            selectMapId = d.getIntSafe("selectMapId")
            totalAwake = d.getIntSafe("totalAwake")
            
            // Rating reset to 0
            gradeRating = 0
            musicRating = 0
            playerRating = 0
            highestRating = 0
            playerOldRating = 0
            playerNewRating = 0
            
            gradeRank = d.getIntSafe("gradeRank")
            classRank = d.getIntSafe("classRank")
            courseRank = d.getIntSafe("courseRank")
            charaSlot = parseSlot(d.get("charaSlot"))
            charaLockSlot = parseSlot(d.get("charaLockSlot"))
            contentBit = d.getLongSafe("contentBit")
            playCount = d.getIntSafe("playCount")
            currentPlayCount = d.getIntSafe("currentPlayCount")
            renameCredit = d.getIntSafe("renameCredit")
            mapStock = null
            
            // Session fields from TARGET_META
            eventWatchedDate = TARGET_META["eventWatchedDate"] as String
            lastGameId = TARGET_META["lastGameId"] as String
            lastRomVersion = TARGET_META["lastRomVersion"] as String
            lastDataVersion = TARGET_META["lastDataVersion"] as String
            lastLoginDate = TARGET_META["lastLoginDate"] as String
            lastPlayDate = TARGET_META["lastPlayDate"] as String
            lastPlayCredit = TARGET_META["lastPlayCredit"] as Int
            lastPlayMode = TARGET_META["lastPlayMode"] as Int
            lastPlaceId = TARGET_META["lastPlaceId"] as Int
            lastPlaceName = TARGET_META["lastPlaceName"] as String
            lastAllNetId = TARGET_META["lastAllNetId"] as Int
            lastRegionId = TARGET_META["lastRegionId"] as Int
            lastRegionName = TARGET_META["lastRegionName"] as String
            lastClientId = TARGET_META["lastClientId"] as String
            lastCountryCode = TARGET_META["lastCountryCode"] as String
            lastSelectEMoney = TARGET_META["lastSelectEMoney"] as Int
            lastSelectTicket = TARGET_META["lastSelectTicket"] as Int
            lastSelectCourse = TARGET_META["lastSelectCourse"] as Int
            lastCountCourse = TARGET_META["lastCountCourse"] as Int
            firstGameId = TARGET_META["firstGameId"] as String
            firstRomVersion = TARGET_META["firstRomVersion"] as String
            firstDataVersion = TARGET_META["firstDataVersion"] as String
            firstPlayDate = TARGET_META["firstPlayDate"] as String
            compatibleCmVersion = TARGET_META["compatibleCmVersion"] as String
            dailyBonusDate = TARGET_META["dailyBonusDate"] as String
            dailyCourseBonusDate = TARGET_META["dailyCourseBonusDate"] as String
            lastPairLoginDate = TARGET_META["lastPairLoginDate"] as String
            lastTrialPlayDate = TARGET_META["lastTrialPlayDate"] as String

            playVsCount = d.getIntSafe("playVsCount")
            playSyncCount = d.getIntSafe("playSyncCount")
            winCount = d.getIntSafe("winCount")
            helpCount = d.getIntSafe("helpCount")
            comboCount = d.getIntSafe("comboCount")
            totalDeluxscore = d.getLongSafe("totalDeluxscore")
            totalBasicDeluxscore = d.getLongSafe("totalBasicDeluxscore")
            totalAdvancedDeluxscore = d.getLongSafe("totalAdvancedDeluxscore")
            totalExpertDeluxscore = d.getLongSafe("totalExpertDeluxscore")
            totalMasterDeluxscore = d.getLongSafe("totalMasterDeluxscore")
            totalReMasterDeluxscore = d.getLongSafe("totalReMasterDeluxscore")
            
            totalHiscore = 0
            totalBasicHighscore = 0
            totalAdvancedHighscore = 0
            totalExpertHighscore = 0
            totalMasterHighscore = 0
            totalReMasterHighscore = 0
            
            totalSync = d.getIntSafe("totalSync")
            totalBasicSync = d.getIntSafe("totalBasicSync")
            totalAdvancedSync = d.getIntSafe("totalAdvancedSync")
            totalExpertSync = d.getIntSafe("totalExpertSync")
            totalMasterSync = d.getIntSafe("totalMasterSync")
            totalReMasterSync = d.getIntSafe("totalReMasterSync")
            totalAchievement = d.getLongSafe("totalAchievement")
            totalBasicAchievement = d.getLongSafe("totalBasicAchievement")
            totalAdvancedAchievement = d.getLongSafe("totalAdvancedAchievement")
            totalExpertAchievement = d.getLongSafe("totalExpertAchievement")
            totalMasterAchievement = d.getLongSafe("totalMasterAchievement")
            totalReMasterAchievement = d.getLongSafe("totalReMasterAchievement")
            
            banState = 0
            dateTime = nowEpochSec
            cmLastEmoneyBrand = d.getIntSafe("cmLastEmoneyBrand")
            cmLastEmoneyCredit = d.getIntSafe("cmLastEmoneyCredit")
            point = d.getIntSafe("point")
            totalPoint = d.getIntSafe("totalPoint")
            friendRegistSkip = d.getIntSafe("friendRegistSkip")
            this.accessCode = accessCode.trim()
        }

        // 3. UserExtend
        val firstMusic = musicList.firstOrNull()
        val userExtend = UserExtend().apply {
            selectMusicId = firstMusic?.musicId ?: 1
            selectDifficultyId = firstMusic?.level ?: 0
        }

        // 4. UserOption
        val srcOption = src.getAsJsonObject("userOption")
        val userOption = UserOption().apply {
            if (srcOption != null) {
                optionKind = srcOption.getIntSafe("optionKind", 1)
                noteSpeed = srcOption.getIntSafe("noteSpeed")
                slideSpeed = srcOption.getIntSafe("slideSpeed")
                touchSpeed = srcOption.getIntSafe("touchSpeed")
                tapDesign = srcOption.getIntSafe("tapDesign", 1)
                holdDesign = srcOption.getIntSafe("holdDesign", 1)
                slideDesign = srcOption.getIntSafe("slideDesign", 1)
                starType = srcOption.getIntSafe("starType")
                outlineDesign = srcOption.getIntSafe("outlineDesign", 1)
                noteSize = srcOption.getIntSafe("noteSize")
                slideSize = srcOption.getIntSafe("slideSize")
                touchSize = srcOption.getIntSafe("touchSize")
                starRotate = srcOption.getIntSafe("starRotate")
                dispCenter = srcOption.getIntSafe("dispCenter")
                outFrameType = srcOption.getIntSafe("outFrameType", 1)
                dispChain = srcOption.getIntSafe("dispChain")
                dispRate = srcOption.getIntSafe("dispRate")
                dispBar = srcOption.getIntSafe("dispBar")
                touchEffect = srcOption.getIntSafe("touchEffect")
                submonitorAnimation = srcOption.getIntSafe("submonitorAnimation")
                submonitorAchive = srcOption.getIntSafe("submonitorAchive")
                submonitorAppeal = srcOption.getIntSafe("submonitorAppeal")
                matching = srcOption.getIntSafe("matching")
                trackSkip = srcOption.getIntSafe("trackSkip")
                brightness = srcOption.getIntSafe("brightness")
                mirrorMode = srcOption.getIntSafe("mirrorMode")
                dispJudge = srcOption.getIntSafe("dispJudge")
                dispJudgePos = srcOption.getIntSafe("dispJudgePos")
                dispJudgeTouchPos = srcOption.getIntSafe("dispJudgeTouchPos")
                adjustTiming = srcOption.getIntSafe("adjustTiming")
                judgeTiming = srcOption.getIntSafe("judgeTiming")
                ansVolume = srcOption.getIntSafe("ansVolume")
                tapHoldVolume = srcOption.getIntSafe("tapHoldVolume")
                criticalSe = srcOption.getIntSafe("criticalSe")
                tapSe = srcOption.getIntSafe("tapSe")
                breakSe = srcOption.getIntSafe("breakSe")
                breakVolume = srcOption.getIntSafe("breakVolume")
                exSe = srcOption.getIntSafe("exSe")
                exVolume = srcOption.getIntSafe("exVolume")
                slideSe = srcOption.getIntSafe("slideSe")
                slideVolume = srcOption.getIntSafe("slideVolume")
                breakSlideVolume = srcOption.getIntSafe("breakSlideVolume")
                touchVolume = srcOption.getIntSafe("touchVolume")
                touchHoldVolume = srcOption.getIntSafe("touchHoldVolume")
                damageSeVolume = srcOption.getIntSafe("damageSeVolume")
                headPhoneVolume = srcOption.getIntSafe("headPhoneVolume")
                sortTab = srcOption.getIntSafe("sortTab")
                sortMusic = srcOption.getIntSafe("sortMusic")
            }
        }

        // 5. Items
        val itemList = mutableListOf<UserItem>()
        src.getAsJsonObject("colls")?.entrySet()?.forEach { (_, block) ->
            block.asJsonObject.getAsJsonArray("userItemList")?.forEach { item ->
                val it = item.asJsonObject
                itemList.add(UserItem(
                    itemKind = it.getIntSafe("itemKind"),
                    itemId = it.getIntSafe("itemId"),
                    stock = it.getIntSafe("stock", 1),
                    isValid = it.getBooleanSafe("isValid", true)
                ))
            }
        }

        // 6. Characters
        val charList = mutableListOf<UserCharacter>()
        val charContainer = src.getAsJsonObject("userCharacter")
        charContainer?.getAsJsonArray("userCharacterList")?.forEach { c ->
            val co = c.asJsonObject
            charList.add(UserCharacter(
                characterId = co.getIntSafe("characterId"),
                point = co.getIntSafe("point"),
                count = 0, // Default 0
                level = co.getIntSafe("level"),
                nextAwake = co.getIntSafe("nextAwake"),
                nextAwakePercent = co.getIntSafe("nextAwakePercent"),
                favorite = false, // Default false
                awakening = co.getIntSafe("awakening"),
                useCount = co.getIntSafe("useCount")
            ))
        }

        // 7. Maps
        val mapList = mutableListOf<UserMap>()
        val mapContainer = src.getAsJsonObject("map")
        mapContainer?.getAsJsonArray("userMapList")?.forEach { m ->
            val mo = m.asJsonObject
            mapList.add(UserMap(
                mapId = mo.getIntSafe("mapId"),
                distance = mo.getIntSafe("distance"),
                isLock = mo.getBooleanSafe("isLock"),
                isClear = mo.getBooleanSafe("isClear"),
                isComplete = mo.getBooleanSafe("isComplete"),
                unlockFlag = mo.getBooleanSafe("unlockFlag")
            ))
        }

        // 8. Kaleidx
        val kaleidxList = mutableListOf<UserKaleidxScope>()
        val kaleidxContainer = src.getAsJsonObject("kaleidx")
        kaleidxContainer?.getAsJsonArray("userKaleidxScopeList")?.forEach { gate ->
            val g = gate.asJsonObject
            kaleidxList.add(UserKaleidxScope(
                gateId = g.getIntSafe("gateId"),
                totalRestLife = g.getIntSafe("totalRestlife"),
                totalAchievement = g.getIntSafe("totalAchievement"),
                totalDeluxscore = g.getIntSafe("totalDeluxscore"),
                bestAchievement = g.getIntSafe("bestAchievement"),
                bestDeluxscore = g.getIntSafe("bestDeluxscore"),
                // Backfill date fields with "" if missing
                bestAchievementDate = g.getStringSafe("bestAchievementDate", ""),
                bestDeluxscoreDate = g.getStringSafe("bestDeluxscoreDate", ""),
                playCount = g.getIntSafe("playCount"),
                clearDate = g.getStringSafe("clearDate", ""),
                lastPlayDate = g.getStringSafe("lastPlayDate", ""),
                isGateFound = g.getBooleanSafe("isGateFound"),
                isKeyFound = g.getBooleanSafe("isKeyFound"),
                isClear = g.getBooleanSafe("isClear"),
                isInfoWatched = g.getBooleanSafe("isInfoWatched")
            ))
        }

        // 9. Intimate
        val intimateList = mutableListOf<UserIntimate>()
        val intimateContainer = src.getAsJsonObject("userIntimate")
        intimateContainer?.getAsJsonArray("userIntimateList")?.forEach { i ->
            val io = i.asJsonObject
            intimateList.add(UserIntimate(
                partnerId = io.getIntSafe("partnerId"),
                intimateLevel = io.getIntSafe("intimateLevel"),
                intimateCountRewarded = io.getIntSafe("intimateCountRewarded")
            ))
        }

        // 10. Weekly Data
        val now = Calendar.getInstance()
        val df = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val day = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7
        now.add(Calendar.DATE, -day)
        val currentWeek = df.format(now.time)
        now.add(Calendar.DATE, -7)
        val lastWeek = df.format(now.time)

        return SaveData(
            gameId = "SDEZ",
            userData = userData,
            userExtend = userExtend,
            userOption = userOption,
            userCharacterList = charList,
            userItemList = itemList,
            userMapList = mapList,
            userMusicDetailList = musicList,
            userIntimateList = intimateList,
            userFavoriteList = mutableListOf(3, 1, 2, 10, 11).map { kind -> UserFavorite(itemKind = kind) },
            userUdemae = UserUdemae(),
            userWeeklyDataList = UserWeeklyData(currentWeek, lastWeek, true),
            serverMissionUserPointData = ServerMissionPointData(),
            userKaleidxScopeList = kaleidxList
        )
    }

    fun merge(main: SaveData, sub: SaveData) {
        requireTargetFormat(main, "主存档")
        requireTargetFormat(sub, "副存档")

        // 1. UserMusicDetailList (Key: musicId:level)
        val musicMap = main.userMusicDetailList.associateBy { "${it.musicId}:${it.level}" }.toMutableMap()
        for (d in sub.userMusicDetailList) {
            val key = "${d.musicId}:${d.level}"
            val cur = musicMap[key]
            if (cur == null) {
                musicMap[key] = d
            } else {
                if (d.achievement > cur.achievement || (d.achievement == cur.achievement && d.deluxscoreMax > cur.deluxscoreMax)) {
                    musicMap[key] = d // Replace entirely with winning record
                }
            }
        }
        (main.userMusicDetailList as MutableList).clear()
        (main.userMusicDetailList as MutableList).addAll(musicMap.values)

        // 2. UserItemList (Key: itemKind:itemId)
        val itemMap = main.userItemList.associateBy { "${it.itemKind}:${it.itemId}" }.toMutableMap()
        for (it in sub.userItemList) {
            val key = "${it.itemKind}:${it.itemId}"
            val cur = itemMap[key]
            if (cur == null) {
                itemMap[key] = it
            } else {
                cur.isValid = cur.isValid || it.isValid
                cur.stock = maxOf(cur.stock, it.stock)
            }
        }
        (main.userItemList as MutableList).clear()
        (main.userItemList as MutableList).addAll(itemMap.values)

        // 3. UserKaleidxScopeList (Key: gateId)
        val gateMap = main.userKaleidxScopeList.associateBy { it.gateId }.toMutableMap()
        for (g in sub.userKaleidxScopeList) {
            val cur = gateMap[g.gateId]
            if (cur == null) {
                gateMap[g.gateId] = g
            } else {
                cur.isGateFound = cur.isGateFound || g.isGateFound
                cur.isKeyFound = cur.isKeyFound || g.isKeyFound
                cur.isClear = cur.isClear || g.isClear
                cur.isInfoWatched = cur.isInfoWatched || g.isInfoWatched

                if (g.bestAchievement > cur.bestAchievement) {
                    cur.bestAchievement = g.bestAchievement
                    cur.bestAchievementDate = g.bestAchievementDate
                }
                if (g.bestDeluxscore > cur.bestDeluxscore) {
                    cur.bestDeluxscore = g.bestDeluxscore
                    cur.bestDeluxscoreDate = g.bestDeluxscoreDate
                }
                cur.totalAchievement = maxOf(cur.totalAchievement, g.totalAchievement)
                cur.totalDeluxscore = maxOf(cur.totalDeluxscore, g.totalDeluxscore)
                cur.playCount += g.playCount

                val gdLast = g.lastPlayDate
                val cdLast = cur.lastPlayDate
                if (gdLast.isNotEmpty() && (cdLast.isEmpty() || gdLast > cdLast)) {
                    cur.lastPlayDate = gdLast
                }

                val gdClear = g.clearDate
                val cdClear = cur.clearDate
                if (gdClear.isNotEmpty() && (cdClear.isEmpty() || gdClear > cdClear)) {
                    cur.clearDate = gdClear
                }
            }
        }
        (main.userKaleidxScopeList as MutableList).clear()
        (main.userKaleidxScopeList as MutableList).addAll(gateMap.values)

        // 4. UserMapList (Key: mapId)
        val mapMap = main.userMapList.associateBy { it.mapId }.toMutableMap()
        for (m in sub.userMapList) {
            val cur = mapMap[m.mapId]
            if (cur == null) {
                mapMap[m.mapId] = m
            } else {
                cur.isClear = cur.isClear || m.isClear
                cur.isComplete = cur.isComplete || m.isComplete
                cur.unlockFlag = cur.unlockFlag || m.unlockFlag
                cur.isLock = cur.isLock && m.isLock // Logical AND (true only if both locked)
                cur.distance = maxOf(cur.distance, m.distance)
            }
        }
        (main.userMapList as MutableList).clear()
        (main.userMapList as MutableList).addAll(mapMap.values)

        // All other fields in main (userData, userOption, userCharacterList, etc.) remain untouched!
    }

    private fun parseSlot(element: JsonElement?): List<Int> {
        if (element == null || element.isJsonNull) return emptyList()
        return try {
            if (element.isJsonArray) {
                element.asJsonArray.map { 
                    if (it.isJsonObject) it.asJsonObject.get("id")?.asInt ?: it.asJsonObject.get("characterId")?.asInt ?: 0
                    else it.asInt 
                }
            } else {
                element.asString.split(",").filter { it.isNotBlank() }.map { it.toInt() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun unlockItem(save: SaveData, kind: Int, id: Int) {
        val list = save.userItemList as MutableList
        val entry = list.find { it.itemKind == kind && it.itemId == id }
        if (entry != null) {
            entry.isValid = true
            entry.stock = maxOf(entry.stock, 1)
        } else {
            list.add(UserItem(itemKind = kind, itemId = id, stock = 1, isValid = true))
        }
    }

    fun lockItem(save: SaveData, kind: Int, id: Int) {
        (save.userItemList as MutableList).removeAll { it.itemKind == kind && it.itemId == id }
    }

    fun setKaleidxGate(save: SaveData, gateId: Int, found: Boolean, keyFound: Boolean = false) {
        val list = save.userKaleidxScopeList as MutableList
        val g = list.find { it.gateId == gateId }
            ?: UserKaleidxScope(gateId = gateId).also { list.add(it) }
        g.isGateFound = found
        if (!found) {
            g.isKeyFound = false
        } else if (keyFound) {
            g.isKeyFound = true
        }
    }

    fun setKaleidxKey(save: SaveData, gateId: Int, found: Boolean) {
        val list = save.userKaleidxScopeList as MutableList
        val g = list.find { it.gateId == gateId }
            ?: UserKaleidxScope(gateId = gateId).also { list.add(it) }
        g.isGateFound = true
        g.isKeyFound = found
    }

    fun setMapStatus(save: SaveData, mapId: Int, isClear: Boolean, isComplete: Boolean) {
        if (BROKEN_MAP_IDS.contains(mapId)) return
        val list = save.userMapList as MutableList
        val m = list.find { it.mapId == mapId }
            ?: UserMap(mapId = mapId).also { list.add(it) }
        m.isClear = isClear
        m.isComplete = isComplete
    }

    fun batchSetMapStatus(save: SaveData, isClear: Boolean, isComplete: Boolean) {
        ALL_MAP_IDS.filterNot { BROKEN_MAP_IDS.contains(it) }.forEach { mapId ->
            setMapStatus(save, mapId, isClear, isComplete)
        }
    }

    fun updateRating(save: SaveData, rating: Int) {
        save.userData?.playerRating = rating
    }

    fun updateVersions(save: SaveData, romVersion: String?, dataVersion: String?) {
        romVersion?.let { save.userData?.lastRomVersion = it }
        dataVersion?.let { save.userData?.lastDataVersion = it }
    }

    fun updateUsername(save: SaveData, name: String) {
        save.userData?.userName = name
    }

    fun updateEquip(save: SaveData, field: String, id: Int) {
        val data = save.userData ?: return
        when (field) {
            "frameId" -> data.frameId = id
            "iconId" -> data.iconId = id
            "plateId" -> data.plateId = id
            "partnerId" -> data.partnerId = id
            "nameplateId" -> data.nameplateId = id
            "titleId" -> data.titleId = id
        }
    }

    fun updateClassRank(save: SaveData, rank: Int) {
        save.userData?.classRank = rank
    }

    fun updatePlayCount(save: SaveData, count: Int) {
        save.userData?.playCount = count
    }

    fun updatePoint(save: SaveData, point: Int) {
        save.userData?.point = point
    }

    fun updateCharacterLevel(save: SaveData, charaId: Int, level: Int) {
        val list = save.userCharacterList as MutableList
        val c = list.find { it.characterId == charaId }
        if (c != null) {
            c.level = level
        } else {
            list.add(UserCharacter(characterId = charaId, level = level))
        }
    }

    fun updatePartnerIntimacy(save: SaveData, partnerId: Int, level: Int) {
        val list = save.userIntimateList as MutableList
        val itm = list.find { it.partnerId == partnerId }
        if (itm != null) {
            itm.intimateLevel = level
        } else {
            list.add(UserIntimate(partnerId = partnerId, intimateLevel = level))
        }
    }

    fun updateMusicScore(
        save: SaveData,
        musicId: Int,
        level: Int,
        achievementInput: Int,
        deluxscore: Int,
        fc: Int,
        sync: Int
    ) {
        val list = save.userMusicDetailList as MutableList
        val d = list.find { it.musicId == musicId && it.level == level }
            ?: MusicDetail(musicId = musicId, level = level).also { list.add(it) }

        // Logic from Section 5.2
        d.achievement = achievementInput
        d.deluxscoreMax = deluxscore
        d.comboStatus = fc
        d.syncStatus = sync
        if (d.playCount == 0) d.playCount = 1
    }
}
