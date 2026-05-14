package com.scut.chudadi.ai

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameState

class GreedyStrategy : PlayStrategy {
    override fun chooseCards(
        state: GameState,
        handCards: List<Card>
    ): List<Card>? {
        return PlayCandidateFinder.findValidCandidates(state, handCards).firstOrNull()
    }
}
