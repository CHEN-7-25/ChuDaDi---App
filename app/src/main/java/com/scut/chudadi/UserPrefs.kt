package com.scut.chudadi

import android.content.Context

/** 用户偏好和个人统计的 SharedPreferences 封装。 */
class UserPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        /** 统一维护 preference key，避免页面之间写错字段名。 */
        private const val PREF_NAME = "chudadi_user_prefs"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_TOTAL_GAMES = "total_games"
        private const val KEY_TOTAL_SCORE = "total_score"
        private const val KEY_BIG_WIN_COUNT = "big_win_count"
        private const val KEY_SMALL_WIN_COUNT = "small_win_count"
        private const val KEY_MUSIC_ENABLED = "music_enabled"
        private const val KEY_BGM_TRACK = "bgm_track"
        private var INSTANCE: UserPrefs? = null
        const val DEFAULT_NICKNAME = "玩家1"

        /** Application 启动后先初始化，内部持有 applicationContext 防止泄漏 Activity。 */
        fun init(context: Context) {
            if (INSTANCE == null) INSTANCE = UserPrefs(context.applicationContext)
        }

        /** 获取单例；未初始化时直接报错，方便开发期发现调用顺序问题。 */
        fun instance(): UserPrefs {
            return INSTANCE ?: throw IllegalStateException("UserPrefs not initialized. Call UserPrefs.init(context) first.")
        }
    }

    /** 玩家昵称，默认显示为“玩家1”。 */
    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, DEFAULT_NICKNAME) ?: DEFAULT_NICKNAME
        set(value) {
            prefs.edit().putString(KEY_NICKNAME, value).apply()
        }

    /** 已完成对局数，只通过 incrementTotalGames 增加。 */
    val totalGames: Int
        get() = prefs.getInt(KEY_TOTAL_GAMES, 0)

    /** 本地玩家累计总分。 */
    var totalScore: Int
        get() = prefs.getInt(KEY_TOTAL_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_SCORE, value).apply()

    /** 获得 +3 的大胜次数。 */
    var bigWinCount: Int
        get() = prefs.getInt(KEY_BIG_WIN_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_BIG_WIN_COUNT, value).apply()

    /** 获得 +1 的小胜次数。 */
    var smallWinCount: Int
        get() = prefs.getInt(KEY_SMALL_WIN_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_SMALL_WIN_COUNT, value).apply()

    /** 背景音乐总开关。 */
    var isMusicEnabled: Boolean
        get() = prefs.getBoolean(KEY_MUSIC_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MUSIC_ENABLED, value).apply()
        }

    /** 当前选择的背景音乐 id，对应 BgmTrack.id。 */
    var selectedBgmTrack: String
        get() = prefs.getString(KEY_BGM_TRACK, "bgm_default") ?: "bgm_default"
        set(value) {
            prefs.edit().putString(KEY_BGM_TRACK, value).apply()
        }

    /** 对局结束后增加总局数，并返回新的总局数。 */
    fun incrementTotalGames(): Int {
        val next = totalGames + 1
        prefs.edit().putInt(KEY_TOTAL_GAMES, next).apply()
        return next
    }

    /** 记录一局得分，并同步维护大胜/小胜次数。 */
    fun addScoreRecord(score: Int) {
        totalScore += score
        if (score == 3) {
            bigWinCount += 1
        } else if (score == 1) {
            smallWinCount += 1
        }
    }
}
