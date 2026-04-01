package com.scut.chudadi.ui

import com.scut.chudadi.ai.PlayStrategy
import com.scut.chudadi.controller.GameController
import com.scut.chudadi.model.Card

class GameViewModel(private val controller: GameController) {
    fun onStartGame() {
        controller.startGame()
    }

    fun onPlay(playerId: String, cards: List<Card>): Boolean {
        return controller.playCards(playerId, cards)
    }

    fun onPass(playerId: String): Boolean {
        return controller.pass(playerId)
    }

    fun onAiTurn(playerId: String, strategy: PlayStrategy): Boolean {
        val state = controller.state
        val player = state.players.firstOrNull { it.id == playerId } ?: return false
        val selected = strategy.chooseCards(state, player.handCards, controller.ruleProfile)
        return if (selected == null) controller.pass(playerId) else controller.playCards(playerId, selected)
    }
}
