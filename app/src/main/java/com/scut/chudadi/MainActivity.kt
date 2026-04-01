package com.scut.chudadi

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.scut.chudadi.controller.GameController
import com.scut.chudadi.model.GameConfig
import com.scut.chudadi.model.PlayerState
import com.scut.chudadi.model.RuleSetType
import com.scut.chudadi.model.ScoringMode

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val resultView = findViewById<TextView>(R.id.tvResult)
        val startButton = findViewById<Button>(R.id.btnStart)

        startButton.setOnClickListener {
            val players = listOf(
                PlayerState(id = "p1", name = "玩家1", isAi = false),
                PlayerState(id = "p2", name = "玩家2", isAi = true),
                PlayerState(id = "p3", name = "玩家3", isAi = true),
                PlayerState(id = "p4", name = "玩家4", isAi = true)
            )
            val config = GameConfig(scoringMode = ScoringMode.SCORE, ruleSetType = RuleSetType.SOUTH)
            val controller = GameController(config, players)
            controller.startGame()

            val current = controller.state.players[controller.state.currentPlayerIndex]
            resultView.text = "演示已开始：${config.ruleSetType}，当前先手是 ${current.name}"
        }
    }
}
