package com.scut.chudadi.ai

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameState
import com.scut.chudadi.rule.RuleProfile

interface PlayStrategy {
    fun chooseCards(state: GameState, handCards: List<Card>, ruleProfile: RuleProfile): List<Card>?
}
