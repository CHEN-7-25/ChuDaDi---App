package com.scut.chudadi.audio

import android.content.Context
import android.media.MediaPlayer
import com.scut.chudadi.UserPrefs

/**
 * 掌管整个游戏的背景音乐播放
 * 采用单例模式
 */
class MusicManager private constructor(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val prefs = UserPrefs.instance()
    
    // 当前播放的曲目
    private var currentTrack: BgmTrack = BgmTrack.fromId(prefs.selectedBgmTrack)

    companion object {
        @Volatile
        private var INSTANCE: MusicManager? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = MusicManager(context.applicationContext)
                    }
                }
            }
        }

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
     * 暂停音乐 (切后台或设置暂停时使用)
     */
    fun pause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    /**
     * 停止背景音乐并释放资源
     */
    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /**
     * 根据设置界面的开关来开启/关闭音乐
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
     * 从UI接收用户选择的新背景音乐并切换
     */
    fun changeTrack(track: BgmTrack) {
        prefs.selectedBgmTrack = track.id
        currentTrack = track
        
        // 如果音乐当前是开启状态，立即播放新的
        if (prefs.isMusicEnabled) {
            stop() // 释放旧的
            play() // 创建并播放新的
        }
    }

    private fun createMediaPlayer(track: BgmTrack) {
        try {
            mediaPlayer = MediaPlayer.create(context, track.resId)?.apply {
                isLooping = true
                setVolume(0.5f, 0.5f) // 设置合理的背景音量
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果报错（比如找不到音频文件），静默失败，避免程序崩溃
            mediaPlayer = null
        }
    }
}
