package com.savetool.maimai.models

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class UserData(
    var userName: String = "",
    var friendCode: String = "",
    var isNetMember: Int = 0,
    var nameplateId: Int = 0,
    var iconId: Int = 0,
    var trophyId: Int = 0,
    var plateId: Int = 0,
    var titleId: Int = 0,
    var partnerId: Int = 0,
    var frameId: Int = 0,
    var selectMapId: Int = 0,
    var charaSlot: List<Int> = mutableListOf(),
    var charaLockSlot: List<Int> = mutableListOf(),
    var totalAwake: Int = 0,
    var gradeRating: Int = 0,
    var musicRating: Int = 0,
    var playerRating: Int = 0,
    var highestRating: Int = 0,
    var playerOldRating: Int = 0,
    var playerNewRating: Int = 0,
    var gradeRank: Int = 0,
    var classRank: Int = 0,
    var courseRank: Int = 0,
    var contentBit: Long = 0,
    var playCount: Int = 0,
    var currentPlayCount: Int = 0,
    var renameCredit: Int = 0,
    var mapStock: JsonElement? = null,
    var eventWatchedDate: String = "",
    var lastGameId: String = "",
    var lastRomVersion: String = "",
    var lastDataVersion: String = "",
    var lastLoginDate: String = "",
    var lastPlayDate: String = "",
    var lastPlayCredit: Int = 0,
    var lastPlayMode: Int = 0,
    var lastPlaceId: Int = 0,
    var lastPlaceName: String = "",
    var lastAllNetId: Int = 0,
    var lastRegionId: Int = 0,
    var lastRegionName: String = "",
    var lastClientId: String = "",
    var lastCountryCode: String = "",
    var lastSelectEMoney: Int = 0,
    var lastSelectTicket: Int = 0,
    var lastSelectCourse: Int = 0,
    var lastCountCourse: Int = 0,
    var firstGameId: String = "",
    var firstRomVersion: String = "",
    var firstDataVersion: String = "",
    var firstPlayDate: String = "",
    var compatibleCmVersion: String = "",
    var dailyBonusDate: String = "",
    var dailyCourseBonusDate: String = "",
    var lastPairLoginDate: String = "",
    var lastTrialPlayDate: String = "",
    var playVsCount: Int = 0,
    var playSyncCount: Int = 0,
    var winCount: Int = 0,
    var helpCount: Int = 0,
    var comboCount: Int = 0,
    var totalDeluxscore: Long = 0,
    var totalBasicDeluxscore: Long = 0,
    var totalAdvancedDeluxscore: Long = 0,
    var totalExpertDeluxscore: Long = 0,
    var totalMasterDeluxscore: Long = 0,
    var totalReMasterDeluxscore: Long = 0,
    var totalHiscore: Long = 0,
    var totalBasicHighscore: Long = 0,
    var totalAdvancedHighscore: Long = 0,
    var totalExpertHighscore: Long = 0,
    var totalMasterHighscore: Long = 0,
    var totalReMasterHighscore: Long = 0,
    var totalSync: Int = 0,
    var totalBasicSync: Int = 0,
    var totalAdvancedSync: Int = 0,
    var totalExpertSync: Int = 0,
    var totalMasterSync: Int = 0,
    var totalReMasterSync: Int = 0,
    var totalAchievement: Long = 0,
    var totalBasicAchievement: Long = 0,
    var totalAdvancedAchievement: Long = 0,
    var totalExpertAchievement: Long = 0,
    var totalMasterAchievement: Long = 0,
    var totalReMasterAchievement: Long = 0,
    var banState: Int = 0,
    var dateTime: Long = 0,
    var cmLastEmoneyBrand: Int = 0,
    var cmLastEmoneyCredit: Int = 0,
    var point: Int = 0,
    var totalPoint: Int = 0,
    @SerializedName("friend_regist_skip") var friendRegistSkip: Int = 0,
    var accessCode: String = ""
)

data class UserExtend(
    var selectMusicId: Int = 1,
    var selectDifficultyId: Int = 0,
    var categoryIndex: Int = 0,
    var musicIndex: Int = 0,
    var extraFlag: Int = 0,
    var selectScoreType: Int = 0,
    var extendContentBit: Int = 0,
    var isPhotoAgree: Boolean = false,
    var isGotoCodeRead: Boolean = false,
    var selectResultDetails: Boolean = false,
    var sortCategorySetting: Int = 0,
    var sortMusicSetting: Int = 0,
    var playStatusSetting: Int = 0,
    var selectedCardList: List<Int> = mutableListOf(),
    var encountMapNpcList: List<Int> = mutableListOf(),
    var selectResultScoreViewType: Int = 0,
    var defaultDxPassCardType: Int = 0
)

data class UserOption(
    var optionKind: Int = 1,
    var noteSpeed: Int = 0,
    var slideSpeed: Int = 0,
    var touchSpeed: Int = 0,
    var tapDesign: Int = 1,
    var holdDesign: Int = 1,
    var slideDesign: Int = 1,
    var starType: Int = 0,
    var outlineDesign: Int = 1,
    var noteSize: Int = 0,
    var slideSize: Int = 0,
    var touchSize: Int = 0,
    var starRotate: Int = 0,
    var dispCenter: Int = 0,
    var outFrameType: Int = 1,
    var dispChain: Int = 0,
    var dispRate: Int = 0,
    var dispBar: Int = 0,
    var touchEffect: Int = 0,
    var submonitorAnimation: Int = 0,
    var submonitorAchive: Int = 0,
    var submonitorAppeal: Int = 0,
    var matching: Int = 0,
    var trackSkip: Int = 0,
    var brightness: Int = 0,
    var mirrorMode: Int = 0,
    var dispJudge: Int = 0,
    var dispJudgePos: Int = 0,
    var dispJudgeTouchPos: Int = 0,
    var adjustTiming: Int = 0,
    var judgeTiming: Int = 0,
    var ansVolume: Int = 0,
    var tapHoldVolume: Int = 0,
    var criticalSe: Int = 0,
    var tapSe: Int = 0,
    var breakSe: Int = 0,
    var breakVolume: Int = 0,
    var exSe: Int = 0,
    var exVolume: Int = 0,
    var slideSe: Int = 0,
    var slideVolume: Int = 0,
    var breakSlideVolume: Int = 0,
    var touchVolume: Int = 0,
    var touchHoldVolume: Int = 0,
    var damageSeVolume: Int = 0,
    var headPhoneVolume: Int = 0,
    var sortTab: Int = 0,
    var sortMusic: Int = 0
)

data class MusicDetail(
    var musicId: Int = 0,
    var level: Int = 0,
    var playCount: Int = 0,
    var achievement: Int = 0,
    var comboStatus: Int = 0,
    var syncStatus: Int = 0,
    var deluxscoreMax: Int = 0,
    var scoreRank: Int = 0,
    var extNum1: Int = 0
)

data class UserCharacter(
    var characterId: Int = 0,
    var point: Int = 0,
    var count: Int = 0,
    var level: Int = 0,
    var nextAwake: Int = 0,
    var nextAwakePercent: Int = 0,
    var favorite: Boolean = false,
    var awakening: Int = 0,
    var useCount: Int = 0
)

data class UserItem(
    var itemKind: Int = 0,
    var itemId: Int = 0,
    var stock: Int = 1,
    var isValid: Boolean = true
)

data class UserMap(
    var mapId: Int = 0,
    var distance: Int = 0,
    var isLock: Boolean = false,
    var isClear: Boolean = false,
    var isComplete: Boolean = false,
    var unlockFlag: Boolean = false
)

data class UserKaleidxScope(
    var gateId: Int = 0,
    var isGateFound: Boolean = false,
    var isKeyFound: Boolean = false,
    var isClear: Boolean = false,
    var totalAchievement: Int = 0,
    var totalDeluxscore: Int = 0,
    var bestAchievement: Int = 0,
    var bestDeluxscore: Int = 0,
    var bestAchievementDate: String = "",
    var bestDeluxscoreDate: String = "",
    var playCount: Int = 0,
    var clearDate: String = "",
    var lastPlayDate: String = "",
    var isInfoWatched: Boolean = false,
    var totalRestLife: Int = 0
)

data class UserIntimate(
    var partnerId: Int = 0,
    var intimateLevel: Int = 0,
    var intimateCountRewarded: Int = 0
)

data class UserFavorite(
    var itemKind: Int = 0,
    var itemIdList: List<Int> = mutableListOf(),
    var userId: Long = 0
)

data class UserUdemae(
    var rate: Int = 0,
    var maxRate: Int = 0,
    var classValue: Int = 0,
    var maxClassValue: Int = 0,
    var totalWinNum: Int = 0,
    var totalLoseNum: Int = 0,
    var maxWinNum: Int = 0,
    var maxLoseNum: Int = 0,
    var winNum: Int = 0,
    var loseNum: Int = 0,
    var npcTotalWinNum: Int = 0,
    var npcTotalLoseNum: Int = 0,
    var npcMaxWinNum: Int = 0,
    var npcMaxLoseNum: Int = 0,
    var npcWinNum: Int = 0,
    var npcLoseNum: Int = 0
)

data class UserWeeklyData(
    var lastLoginWeek: String = "",
    var beforeLoginWeek: String = "",
    var friendBonusFlag: Boolean = true
)

data class ServerMissionPointData(
    var totalPoints: Int = 0,
    var availablePoints: Int = 0
)

data class UserGeneralData(
    var propertyKey: String = "",
    var propertyValue: String = ""
)

data class UserAct(
    var kind: Int = 0,
    var id: Int = 0,
    var sortNumber: Long = 0,
    var param1: Int = 0,
    var param2: Int = 0,
    var param3: Int = 0,
    var param4: Int = 0
)

data class UserFriendSeasonRanking(
    var seasonId: Int = 0,
    var point: Int = 0,
    var rank: Int = 0,
    var rewardGet: Boolean = false,
    var userName: String = "",
    var recordDate: String = ""
)

data class UserLoginBonus(
    var bonusId: Int = 0,
    var point: Int = 0,
    var isCurrent: Boolean = false,
    var isComplete: Boolean = false
)

data class UserMissionData(
    var updateDate: String = "",
    var type: Int = 0,
    var difficulty: Int = 0,
    var targetGenreId: Int = 0,
    var targetGenreTableId: Int = 0,
    var conditionGenreId: Int = 0,
    var conditionGenreTableId: Int = 0,
    var clearFlag: Boolean = false
)

data class UserCircleData(
    var circleId: Int = 0,
    var lastLoginDate: String = ""
)

data class UserCirclePointData(
    var circleId: Int = 0,
    var userName: String = "",
    var aggrDate: String = "",
    var point: Int = 0,
    var recordDate: String = "",
    var rewardGet: Boolean = false
)

data class UserCirclePointRankingResult(
    var circleId: Int = 0,
    var circleName: String = "",
    var aggrDate: String = "",
    var lastMonthCircleRank: Int = 0,
    var lastMonthPoint: Int = 0
)

data class UserPhotoRecord(
    var orderId: Int = 0,
    var divLength: Int = 0,
    var fileName: String = "",
    var placeId: Int = 0,
    var clientId: String = "",
    var uploadDate: String = "",
    var playlogId: Long = 0,
    var trackNo: Int = 0
)

data class ServerMissionPointChangelog(
    var reason: String = "",
    var changedAmount: Int = 0,
    var recordDate: String = ""
)

data class SaveData(
    var gameId: String = "SDEZ",
    var userData: UserData? = null,
    var userExtend: UserExtend? = null,
    var userOption: UserOption? = null,
    var mapEncountNpcList: List<Any> = mutableListOf(),
    var userActList: List<UserAct> = mutableListOf(),
    var userCardList: List<Any> = mutableListOf(),
    var userCharacterList: List<UserCharacter> = mutableListOf(),
    var userChargeList: List<Any> = mutableListOf(),
    var userCourseList: List<Any> = mutableListOf(),
    var userFavoriteList: List<UserFavorite> = mutableListOf(),
    var userFriendSeasonRankingList: List<UserFriendSeasonRanking> = mutableListOf(),
    var userGeneralDataList: List<UserGeneralData> = mutableListOf(),
    var userGhostList: Any? = null,
    var userItemList: List<UserItem> = mutableListOf(),
    var userLoginBonusList: List<UserLoginBonus> = mutableListOf(),
    var userMapList: List<UserMap> = mutableListOf(),
    var userMusicDetailList: List<MusicDetail> = mutableListOf(),
    var userPlaylogList: List<Any> = mutableListOf(),
    var userRateList: Any? = null,
    var userUdemae: UserUdemae? = null,
    var userWeeklyDataList: UserWeeklyData? = null,
    var userIntimateList: List<UserIntimate> = mutableListOf(),
    var userMissionDataList: List<UserMissionData> = mutableListOf(),
    var userShopStockList: List<Any> = mutableListOf(),
    var userPrintDetailList: List<Any> = mutableListOf(),
    var userKaleidxScopeList: List<UserKaleidxScope> = mutableListOf(),
    var userCircleDataList: List<UserCircleData> = mutableListOf(),
    var userCircleChallengeList: List<Any> = mutableListOf(),
    var userCirclePointDataList: List<UserCirclePointData> = mutableListOf(),
    var userCirclePointRankingResultList: List<UserCirclePointRankingResult>? = null,
    var userJoinCircleRequestList: List<Any> = mutableListOf(),
    var userFestaDataList: List<Any> = mutableListOf(),
    var userResultFestaDataList: List<Any> = mutableListOf(),
    var userPhotoRecordList: List<UserPhotoRecord> = mutableListOf(),
    var serverMissionUserPointData: ServerMissionPointData? = null,
    var serverMissionUserPointChangelogList: List<ServerMissionPointChangelog> = mutableListOf()
)
