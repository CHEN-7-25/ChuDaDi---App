package com.scut.chudadi.ai

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameState
import com.scut.chudadi.rule.HandEvaluator
import com.scut.chudadi.rule.RuleEngine
import com.scut.chudadi.rule.RuleProfile
import com.scut.chudadi.rule.SouthRuleProfile

/** 为提示和 AI 枚举当前手牌中所有合法候选出牌。 */
object PlayCandidateFinder {
    /**
     * 查找可出的牌组。
     *
     * 有上一手时只枚举同张数牌组；桌面为空时枚举 1 到 5 张，交给 RuleEngine 过滤非法牌型。
     */
    fun findValidCandidates(
        state: GameState,
        handCards: List<Card>,
        ruleProfile: RuleProfile = SouthRuleProfile
    ): List<List<Card>> {
        val targetSize = state.lastPlay?.cards?.size
        val sizes = if (targetSize == null) {
            1..minOf(5, handCards.size)
        } else {
            targetSize..targetSize
        }

        return sizes
            .flatMap { size -> combinations(handCards.sorted(), size) }
            .filter { RuleEngine.canPlay(state, handCards, it, ruleProfile) }
            .sortedWith(candidateComparator(ruleProfile))
    }

    /** 候选按牌型、主点数、主花色从小到大排序，方便不同策略选择 first 或 last。 */
    private fun candidateComparator(ruleProfile: RuleProfile): Comparator<List<Card>> {
        return compareBy<List<Card>> { it.size }
            .thenBy { HandEvaluator.evaluate(it, ruleProfile)?.type?.level ?: Int.MAX_VALUE }
            .thenBy { HandEvaluator.evaluate(it, ruleProfile)?.majorRank?.order ?: Int.MAX_VALUE }
            .thenBy { HandEvaluator.evaluate(it, ruleProfile)?.majorSuit?.order ?: Int.MAX_VALUE }
            .thenBy { it.sumOf { card -> card.rank.order * 10 + card.suit.order } }
    }

    /** 回溯枚举固定张数的组合；手牌最多 13 张，当前规模足够轻量。 */
    private fun combinations(cards: List<Card>, size: Int): List<List<Card>> {
        if (size <= 0 || size > cards.size) return emptyList()
        val result = mutableListOf<List<Card>>()

        fun collect(start: Int, current: MutableList<Card>) {
            if (current.size == size) {
                result.add(current.toList())
                return
            }

            val remainingSlots = size - current.size
            val lastStart = cards.size - remainingSlots
            for (index in start..lastStart) {
                current.add(cards[index])
                collect(index + 1, current)
                current.removeAt(current.lastIndex)
            }
        }

        collect(0, mutableListOf())
        return result
    }
}
