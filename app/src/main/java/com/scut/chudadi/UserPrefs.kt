package com.scut.chudadi

import android.content.Context

class UserPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
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

        fun init(context: Context) {
            if (INSTANCE == null) INSTANCE = UserPrefs(context.applicationContext)
        }

        fun instance(): UserPrefs {
            return INSTANCE ?: throw IllegalStateException("UserPrefs not initialized. Call UserPrefs.init(context) first.")
        }
    }

    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, DEFAULT_NICKNAME) ?: DEFAULT_NICKNAME
        set(value) {
            prefs.edit().putString(KEY_NICKNAME, value).apply()
        }

    val totalGames: Int
        get() = prefs.getInt(KEY_TOTAL_GAMES, 0)

    var totalScore: Int
        get() = prefs.getInt(KEY_TOTAL_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_SCORE, value).apply()

    var bigWinCount: Int
        get() = prefs.getInt(KEY_BIG_WIN_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_BIG_WIN_COUNT, value).apply()

    var smallWinCount: Int
        get() = prefs.getInt(KEY_SMALL_WIN_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_SMALL_WIN_COUNT, value).apply()

    var isMusicEnabled: Boolean
        get() = prefs.getBoolean(KEY_MUSIC_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MUSIC_ENABLED, value).apply()
        }

    var selectedBgmTrack: String
        get() = prefs.getString(KEY_BGM_TRACK, "bgm_default") ?: "bgm_default"
        set(value) {
            prefs.edit().putString(KEY_BGM_TRACK, value).apply()
        }

    fun incrementTotalGames(): Int {
        val next = totalGames + 1
        prefs.edit().putInt(KEY_TOTAL_GAMES, next).apply()
        return next
    }

    fun addScoreRecord(score: Int) {
        totalScore += score
        if (score == 3) {
            bigWinCount += 1
        } else if (score == 1) {
            smallWinCount += 1
        }
    }
}
