package com.scut.chudadi.ai

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameState
import com.scut.chudadi.rule.RuleProfile

/** AI 出牌策略接口，允许替换不同选牌风格而不改控制器。 */
interface PlayStrategy {
    /** 返回要出的牌；返回 null 表示当前没有合适出牌，应当过牌。 */
    fun chooseCards(state: GameState, handCards: List<Card>, ruleProfile: RuleProfile): List<Card>?
}
