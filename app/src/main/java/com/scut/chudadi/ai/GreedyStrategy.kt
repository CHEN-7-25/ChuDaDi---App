package com.scut.chudadi.ai

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameState
import com.scut.chudadi.rule.RuleProfile

/** 贪心策略：选择最小合法候选，尽量低成本跟牌。 */
class GreedyStrategy : PlayStrategy {
    override fun chooseCards(
        state: GameState,
        handCards: List<Card>,
        ruleProfile: RuleProfile
    ): List<Card>? {
        return PlayCandidateFinder.findValidCandidates(state, handCards, ruleProfile).firstOrNull()
    }
}
