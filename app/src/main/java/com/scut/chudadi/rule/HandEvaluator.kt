package com.scut.chudadi.rule

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.HandType
import com.scut.chudadi.model.Play
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.Suit

/**
 * 将一组牌识别成具体牌型。
 *
 * 识别成功会返回 Play，失败返回 null；比较大小需要的主点数和主花色也在这里计算。
 */
object HandEvaluator {
    /** 按张数分派到对应牌型识别函数。 */
    fun evaluate(cards: List<Card>, profile: RuleProfile = SouthRuleProfile): Play? {
        if (cards.isEmpty()) return null
        val sorted = cards.sorted()
        return when (sorted.size) {
            1 -> single(sorted)
            2 -> pair(sorted)
            3 -> triple(sorted)
            4 -> bomb4(sorted)
            5 -> fiveCards(sorted, profile)
            else -> null
        }
    }

    /** 单张牌的主值就是自身点数和花色。 */
    private fun single(cards: List<Card>) = Play(cards, HandType.SINGLE, cards[0].rank, cards[0].suit)

    /** 对子要求两张点数相同，主花色取对子中最大的花色。 */
    private fun pair(cards: List<Card>): Play? {
        if (cards[0].rank != cards[1].rank) return null
        val topSuit = cards.maxBy { it.suit.order }.suit
        return Play(cards, HandType.PAIR, cards[0].rank, topSuit)
    }

    /** 三条要求三张点数相同，主花色取三张中最大的花色。 */
    private fun triple(cards: List<Card>): Play? {
        if (cards.any { it.rank != cards[0].rank }) return null
        val topSuit = cards.maxBy { it.suit.order }.suit
        return Play(cards, HandType.TRIPLE, cards[0].rank, topSuit)
    }

    /** 四炸要求四张点数相同；同点数比较不依赖真实花色，因此使用固定黑桃作为主花色。 */
    private fun bomb4(cards: List<Card>): Play? {
        if (cards.any { it.rank != cards[0].rank }) return null
        return Play(cards, HandType.BOMB4, cards[0].rank, Suit.SPADE)
    }

    /** 识别五张牌型时先识别更强、更具体的牌型，避免同花顺被普通同花或顺子提前命中。 */
    private fun fiveCards(cards: List<Card>, profile: RuleProfile): Play? {
        val isFlush = cards.all { it.suit == cards[0].suit }
        val rankCount = cards.groupingBy { it.rank }.eachCount().values.sortedDescending()

        val straightHighRank = straightHigh(cards, profile)

        return when {
            straightHighRank != null && isFlush -> {
                Play(cards, HandType.STRAIGHT_FLUSH, straightHighRank, topSuitForRank(cards, straightHighRank))
            }
            rankCount == listOf(4, 1) -> {
                val main = cards.groupBy { it.rank }.maxBy { it.value.size }.key
                Play(cards, HandType.FOUR_PLUS_ONE, main, topSuitForRank(cards, main))
            }
            rankCount == listOf(3, 2) -> {
                val main = cards.groupBy { it.rank }.maxBy { it.value.size }.key
                Play(cards, HandType.FULL_HOUSE, main, topSuitForRank(cards, main))
            }
            isFlush -> Play(cards, HandType.FLUSH5, cards.maxBy { it.rank.order }.rank, cards.maxBy { it.suit.order }.suit)
            straightHighRank != null -> Play(cards, HandType.STRAIGHT, straightHighRank, topSuitForRank(cards, straightHighRank))
            else -> null
        }
    }

    /** 在同一点数的多张牌中取最大花色，作为南方规则同点数比较的依据。 */
    private fun topSuitForRank(cards: List<Card>, rank: Rank): Suit {
        return cards.filter { it.rank == rank }.maxBy { it.suit.order }.suit
    }

    /** 返回顺子的最高点数；南方规则允许 A2345 时，最高点按 5 处理。 */
    private fun straightHigh(cards: List<Card>, profile: RuleProfile): Rank? {
        val ranks = cards.map { it.rank.order }.sorted()
        val normal = ranks.zipWithNext().all { (a, b) -> b - a == 1 }
        if (normal) return cards.maxBy { it.rank.order }.rank

        if (!profile.allowA2345Straight) return null

        // A2345 特例，A 在最小顺子中作为 1 使用
        val a2345 = ranks == listOf(3, 4, 5, 14, 15)
        if (a2345) return Rank.FIVE
        return null
    }
}
