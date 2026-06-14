package com.scut.chudadi.ui

import com.scut.chudadi.ai.PlayStrategy
import com.scut.chudadi.controller.GameController
import com.scut.chudadi.model.Card

/**
 * 对 GameController 的轻量适配层。
 *
 * 当前 UI 主要仍由 MainActivity 直接编排，本类保留为后续 MVVM 拆分的落点。
 */
class GameViewModel(private val controller: GameController) {
    /** 启动一局新游戏。 */
    fun onStartGame() {
        controller.startGame()
    }

    /** 将 UI 的出牌事件转交给控制器。 */
    fun onPlay(playerId: String, cards: List<Card>): Boolean {
        return controller.playCards(playerId, cards)
    }

    /** 将 UI 的过牌事件转交给控制器。 */
    fun onPass(playerId: String): Boolean {
        return controller.pass(playerId)
    }

    /** 让指定 AI 玩家根据策略行动，无法出牌时自动过牌。 */
    fun onAiTurn(playerId: String, strategy: PlayStrategy): Boolean {
        val state = controller.state
        val player = state.players.firstOrNull { it.id == playerId } ?: return false
        val selected = strategy.chooseCards(state, player.handCards, controller.ruleProfile)
        return if (selected == null) controller.pass(playerId) else controller.playCards(playerId, selected)
    }
}
