package com.scut.chudadi.ai

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameState
import com.scut.chudadi.rule.RuleEngine
import com.scut.chudadi.rule.RuleProfile

class GreedyStrategy : PlayStrategy {
    override fun chooseCards(
        state: GameState,
        handCards: List<Card>,
        ruleProfile: RuleProfile
    ): List<Card>? {
        val sorted = handCards.sorted()
        for (card in sorted) {
            val candidate = listOf(card)
            if (RuleEngine.canPlay(state, handCards, candidate, ruleProfile)) {
                return candidate
            }
        }
        return null
    }
}
