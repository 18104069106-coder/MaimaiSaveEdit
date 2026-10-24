#!/usr/bin/env kotlin

import com.google.gson.annotations.SerializedName

// 1. 数据模型
data class UserData(
    var userName: String? = "",
    var accessCode: String? = "",
    var playerRating: Int = 0,
    var playerOldRating: Int = 0,
    var classRank: Int = 0,
    var playCount: Int = 0,
    var lastRomVersion: String? = "",
    var lastDataVersion: String? = "",
    var frameId: Int = 0,
    var iconId: Int = 0,
    var plateId: Int = 0,
    var partnerId: Int = 0,
    var charaSlot: String? = "",
    var point: Int = 0
)

data class MusicDetail(
    val musicId: Int = 0,
    val level: Int = 0,
    var playCount: Int = 0,
    var achievement: Int = 0,
    var comboStatus: Int = 0,
    var syncStatus: Int = 0,
    var deluxscoreMax: Int = 0,
    var scoreRank: Int = 0,
    var extNum1: Int = 0
)

data class UserItem(
    val itemKind: Int = 0,
    val itemId: Int = 0,
    var stock: Int = 1,
    var isValid: Boolean = true
)

data class SaveData(
    var gameId: String = "SDEZ",
    var userData: UserData? = null,
    var userMusicDetailList: MutableList<MusicDetail> = mutableListOf(),
    var userItemList: MutableList<UserItem> = mutableListOf()
)

// 2. 取优合并逻辑示例
object SaveMerger {
    fun merge(mainSave: SaveData, subSave: SaveData): SaveData {
        // 合并歌曲成绩（取达成率或 DX 分较高的）
        val musicMap = mainSave.userMusicDetailList.associateBy { "${it.musicId}:${it.level}" }.toMutableMap()
        for (subDetail in subSave.userMusicDetailList) {
            val key = "${subDetail.musicId}:${subDetail.level}"
            val existing = musicMap[key]
            if (existing == null) {
                musicMap[key] = subDetail
            } else {
                if (subDetail.achievement > existing.achievement ||
                    (subDetail.achievement == existing.achievement && subDetail.deluxscoreMax > existing.deluxscoreMax)) {
                    musicMap[key] = subDetail
                }
            }
        }
        mainSave.userMusicDetailList = musicMap.values.toMutableList()

        // 合并收藏品
        val itemMap = mainSave.userItemList.associateBy { "${it.itemKind}:${it.itemId}" }.toMutableMap()
        for (subItem in subSave.userItemList) {
            val key = "${subItem.itemKind}:${subItem.itemId}"
            val existing = itemMap[key]
            if (existing == null) {
                itemMap[key] = subItem
            } else {
                existing.isValid = existing.isValid || subItem.isValid
                existing.stock = maxOf(existing.stock, subItem.stock)
            }
        }
        mainSave.userItemList = itemMap.values.toMutableList()

        return mainSave
    }
}