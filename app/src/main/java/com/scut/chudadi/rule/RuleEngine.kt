package com.scut.chudadi.rule

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameState
import com.scut.chudadi.model.HandType
import com.scut.chudadi.model.Play

/**
 * 出牌合法性和牌面大小比较入口。
 *
 * 这里不修改 GameState，只判断“能不能出/过”和“两手牌谁大”，状态推进由 GameController 负责。
 */
object RuleEngine {
    /** 校验玩家要出的牌是否属于手牌、是否成牌型、是否满足首轮和压制关系。 */
    fun canPlay(
        state: GameState,
        playerCards: List<Card>,
        toPlay: List<Card>,
        profile: RuleProfile = SouthRuleProfile
    ): Boolean {
        if (!playerCards.containsAll(toPlay)) return false

        val currentPlay = HandEvaluator.evaluate(toPlay, profile) ?: return false
        // 南方规则首轮必须打出包含方块 3 的牌，北方规则通过 profile 关闭这个限制。
        if (state.firstRound &&
            profile.firstRoundMustContainDiamondThree &&
            !toPlay.contains(Card.DIAMOND_THREE)
        ) {
            return false
        }

        val lastPlay = state.lastPlay ?: return true
        if (lastPlay.cards.size != currentPlay.cards.size) return false

        return compare(currentPlay, lastPlay, profile) > 0
    }

    /**
     * 比较两手同张数牌的大小。
     *
     * 返回值大于 0 表示 current 可以压过 previous；五张牌先比牌型等级，再比主点数和花色。
     */
    fun compare(
        current: Play,
        previous: Play,
        profile: RuleProfile = SouthRuleProfile
    ): Int {
        if (current.type.cardCount != previous.type.cardCount) {
            return current.type.cardCount - previous.type.cardCount
        }

        // 五张牌型之间存在顺子、同花、葫芦等等级差异，其他张数必须同牌型比较。
        if (current.type.cardCount == 5 && current.type != previous.type) {
            return current.type.level - previous.type.level
        }

        val rankDiff = current.majorRank.order - previous.majorRank.order
        if (rankDiff != 0) return rankDiff

        if (!profile.compareSuitWhenMajorRankSame) return 0
        return current.majorSuit.order - previous.majorSuit.order
    }

    /** 桌面没有上一手时不能过牌，玩家必须主动出牌。 */
    fun canPass(state: GameState): Boolean {
        return state.lastPlay != null
    }

    /** 供 UI 或测试快速判断某个牌型是否属于五张牌型。 */
    fun isFiveCardType(type: HandType): Boolean = type.cardCount == 5
}
