package com.scut.chudadi

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class ProfileActivity : AppCompatActivity() {
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

        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            val input = etNickname.text.toString().trim()
            prefs.nickname = if (input.isEmpty()) UserPrefs.DEFAULT_NICKNAME else input
            etNickname.setText(prefs.nickname)
        }
    }

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
