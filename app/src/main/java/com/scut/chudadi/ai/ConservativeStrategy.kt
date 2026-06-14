package com.scut.chudadi.ai

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameState
import com.scut.chudadi.rule.RuleProfile

/** 保守/压制策略：选择较大的合法候选，用于制造不同 AI 风格。 */
class ConservativeStrategy : PlayStrategy {
    override fun chooseCards(
        state: GameState,
        handCards: List<Card>,
        ruleProfile: RuleProfile
    ): List<Card>? {
        return PlayCandidateFinder.findValidCandidates(state, handCards, ruleProfile).lastOrNull()
    }
}
