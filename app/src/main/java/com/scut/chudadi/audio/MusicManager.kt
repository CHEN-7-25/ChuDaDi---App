package com.scut.chudadi.audio

import android.content.Context
import android.media.MediaPlayer
import com.scut.chudadi.UserPrefs

/**
 * 掌管整个游戏的背景音乐播放。
 *
 * 单例持有 applicationContext，并根据 UserPrefs 中的开关和曲目选择维护 MediaPlayer 生命周期。
 */
class MusicManager private constructor(private val context: Context) {

    /** 当前背景音乐播放器；为 null 表示尚未创建或已经释放。 */
    private var mediaPlayer: MediaPlayer? = null
    private val prefs = UserPrefs.instance()
    
    /** 当前播放的曲目，初始化时从用户偏好恢复。 */
    private var currentTrack: BgmTrack = BgmTrack.fromId(prefs.selectedBgmTrack)

    companion object {
        @Volatile
        private var INSTANCE: MusicManager? = null

        /** 初始化全局音乐管理器，通常在 MainActivity.onCreate 中调用。 */
        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = MusicManager(context.applicationContext)
                    }
                }
            }
        }

        /** 获取单例；未初始化时抛错以暴露调用顺序问题。 */
        fun instance(): MusicManager {
            return INSTANCE ?: throw IllegalStateException("MusicManager not initialized. Call MusicManager.init(context) first.")
        }
    }

    /**
     * 播放音乐。会检查用户偏好设置，如果没开启则静音/停止。
     */
    fun play() {
        if (!prefs.isMusicEnabled) {
            stop()
            return
        }

        try {
            if (mediaPlayer == null) {
                createMediaPlayer(currentTrack)
            }
            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 暂停音乐，切后台时保留 MediaPlayer 以便回到前台继续播放。
     */
    fun pause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    /**
     * 停止背景音乐并释放资源。
     */
    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /**
     * 根据设置界面的开关来开启或关闭音乐。
     */
    fun setMusicEnabled(enabled: Boolean) {
        prefs.isMusicEnabled = enabled
        if (enabled) {
            play()
        } else {
            stop()
        }
    }

    /**
     * 从 UI 接收用户选择的新背景音乐并切换。
     */
    fun changeTrack(track: BgmTrack) {
        prefs.selectedBgmTrack = track.id
        currentTrack = track
        
        // 如果音乐当前是开启状态，立即播放新的
        if (prefs.isMusicEnabled) {
            stop()
            play()
        }
    }

    /** 创建循环播放的 MediaPlayer；资源异常时静默降级，避免影响游戏主流程。 */
    private fun createMediaPlayer(track: BgmTrack) {
        try {
            mediaPlayer = MediaPlayer.create(context, track.resId)?.apply {
                isLooping = true
                setVolume(0.5f, 0.5f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mediaPlayer = null
        }
    }
}
