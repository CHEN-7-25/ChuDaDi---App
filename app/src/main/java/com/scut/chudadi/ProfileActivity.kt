package com.scut.chudadi

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** 个人资料页，展示并编辑本地昵称和历史统计。 */
class ProfileActivity : AppCompatActivity() {
    /** 初始化资料页控件，并把 UserPrefs 中的数据填入界面。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        UserPrefs.init(applicationContext)
        val prefs = UserPrefs.instance()

        val etNickname = findViewById<EditText>(R.id.etNickname)
        val btnSave = findViewById<Button>(R.id.btnSaveNickname)
        val tvTotalGames = findViewById<TextView>(R.id.tvTotalGames)
        val tvTotalScore = findViewById<TextView>(R.id.tvTotalScore)
        val tvBigWinCount = findViewById<TextView>(R.id.tvBigWinCount)
        val tvSmallWinCount = findViewById<TextView>(R.id.tvSmallWinCount)
        val btnBack = findViewById<Button>(R.id.btnBack)

        etNickname.setText(prefs.nickname)
        tvTotalGames.text = prefs.totalGames.toString()
        tvTotalScore.text = prefs.totalScore.toString()
        tvBigWinCount.text = prefs.bigWinCount.toString()
        tvSmallWinCount.text = prefs.smallWinCount.toString()

        // 返回按钮只关闭当前资料页，不影响主页面中的牌局状态。
        btnBack.setOnClickListener {
            finish()
        }

        // 空昵称回退默认昵称，避免主界面显示空字符串。
        btnSave.setOnClickListener {
            val input = etNickname.text.toString().trim()
            prefs.nickname = if (input.isEmpty()) UserPrefs.DEFAULT_NICKNAME else input
            etNickname.setText(prefs.nickname)
        }
    }

    /** 回到资料页时刷新统计，保证从对局页返回后数据是最新的。 */
    override fun onResume() {
        super.onResume()
        val tvTotalGames = findViewById<TextView>(R.id.tvTotalGames)
        val tvTotalScore = findViewById<TextView>(R.id.tvTotalScore)
        val tvBigWinCount = findViewById<TextView>(R.id.tvBigWinCount)
        val tvSmallWinCount = findViewById<TextView>(R.id.tvSmallWinCount)
        
        val prefs = UserPrefs.instance()
        tvTotalGames.text = prefs.totalGames.toString()
        tvTotalScore.text = prefs.totalScore.toString()
        tvBigWinCount.text = prefs.bigWinCount.toString()
        tvSmallWinCount.text = prefs.smallWinCount.toString()
    }
}
