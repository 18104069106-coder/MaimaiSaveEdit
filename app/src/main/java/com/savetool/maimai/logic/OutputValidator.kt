package com.savetool.maimai.logic

import com.google.gson.JsonObject

object OutputValidator {
    private val SHAPES: Map<String, Set<String>> = mapOf(
        "userMusicDetailList" to setOf("musicId", "level", "playCount", "achievement", "comboStatus", "syncStatus", "deluxscoreMax", "scoreRank", "extNum1"),
        "userItemList" to setOf("itemKind", "itemId", "stock", "isValid"),
        "userCharacterList" to setOf("characterId", "point", "count", "level", "nextAwake", "nextAwakePercent", "favorite", "awakening", "useCount"),
        "userMapList" to setOf("mapId", "distance", "isLock", "isClear", "isComplete", "unlockFlag"),
        "userIntimateList" to setOf("partnerId", "intimateLevel", "intimateCountRewarded"),
        "userKaleidxScopeList" to setOf(
            "gateId", "isGateFound", "isKeyFound", "isClear", "totalAchievement", "totalDeluxscore",
            "bestAchievement", "bestDeluxscore", "bestAchievementDate", "bestDeluxscoreDate",
            "playCount", "clearDate", "lastPlayDate", "isInfoWatched", "totalRestLife"
        )
    )

    fun validate(out: JsonObject): List<String> {
        val errors = mutableListOf<String>()
        if (out.size() != 37) errors += "顶层 key 应为 37 个，实际 ${out.size()}"
        
        for (k in listOf("userGhostList", "userRateList", "userCirclePointRankingResultList")) {
            val elem = out.get(k)
            if (elem == null || !elem.isJsonNull) errors += "$k 必须是 null"
        }
        
        val ud = out.getAsJsonObject("userData")
        if (ud == null || ud.size() != 93) errors += "userData 应为 93 个字段，实际 ${ud?.size()}"
        if (ud != null && ud.get("accessCode")?.asString.isNullOrBlank()) errors += "accessCode 为空"
        
        val option = out.getAsJsonObject("userOption")
        if (option?.has("tempoVolume") == true) errors += "userOption 不应含 tempoVolume"
        
        val extend = out.getAsJsonObject("userExtend")
        if (extend?.get("selectMusicId")?.asInt == 0) errors += "selectMusicId 不应为 0"
        
        for ((listKey, expected) in SHAPES) {
            val arr = out.getAsJsonArray(listKey)
            if (arr == null) {
                errors += "$listKey 缺失或不是数组"
                continue
            }
            arr.forEachIndexed { i, e ->
                val keys = (e as? JsonObject)?.keySet()
                if (keys != expected) {
                    errors += "$listKey[$i] 字段集合不符：多 ${keys.orEmpty() - expected}，缺 ${expected - keys.orEmpty()}"
                }
            }
        }
        
        if (out.get("userWeeklyDataList")?.isJsonObject != true) errors += "userWeeklyDataList 必须是对象"
        if (out.get("serverMissionUserPointData")?.isJsonObject != true) errors += "serverMissionUserPointData 必须是对象"
        
        return errors
    }
}
