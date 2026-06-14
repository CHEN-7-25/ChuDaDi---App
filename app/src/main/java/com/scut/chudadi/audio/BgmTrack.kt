package com.scut.chudadi.audio

import com.scut.chudadi.R

/** 可选背景音乐列表，id 会持久化到 UserPrefs。 */
enum class BgmTrack(val id: String, val resId: Int, val displayName: String) {
    DEFAULT("bgm_default", R.raw.bgm_default, "默认轻松"),
    EXCITING("bgm_exciting", R.raw.bgm_exciting, "紧张刺激"),
    CLASSIC("bgm_classic", R.raw.bgm_classic, "经典回忆");

    companion object {
        /** preference 中的 id 无效时回退默认曲目。 */
        fun fromId(id: String): BgmTrack {
            return values().find { it.id == id } ?: DEFAULT
        }
    }
}
