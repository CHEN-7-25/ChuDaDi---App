package com.scut.chudadi.rule

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.HandType
import com.scut.chudadi.model.Play
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.Suit

object HandEvaluator {
    fun evaluate(cards: List<Card>, profile: RuleProfile): Play? {
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

    private fun single(cards: List<Card>) = Play(cards, HandType.SINGLE, cards[0].rank, cards[0].suit)

    private fun pair(cards: List<Card>): Play? {
        if (cards[0].rank != cards[1].rank) return null
        val topSuit = cards.maxBy { it.suit.order }.suit
        return Play(cards, HandType.PAIR, cards[0].rank, topSuit)
    }

    private fun triple(cards: List<Card>): Play? {
        if (cards.any { it.rank != cards[0].rank }) return null
        val topSuit = cards.maxBy { it.suit.order }.suit
        return Play(cards, HandType.TRIPLE, cards[0].rank, topSuit)
    }

    private fun bomb4(cards: List<Card>): Play? {
        if (cards.any { it.rank != cards[0].rank }) return null
        return Play(cards, HandType.BOMB4, cards[0].rank, Suit.SPADE)
    }

    private fun fiveCards(cards: List<Card>, profile: RuleProfile): Play? {
        val isFlush = cards.all { it.suit == cards[0].suit }
        val rankCount = cards.groupingBy { it.rank }.eachCount().values.sortedDescending()

        val straightHighRank = straightHigh(cards, profile)
        val isStraight = straightHighRank != null

        return when {
            isStraight && isFlush -> Play(cards, HandType.STRAIGHT_FLUSH, straightHighRank!!, cards[0].suit)
            rankCount == listOf(4, 1) -> {
                val main = cards.groupBy { it.rank }.maxBy { it.value.size }.key
                Play(cards, HandType.FOUR_PLUS_ONE, main)
            }
            rankCount == listOf(3, 2) -> {
                val main = cards.groupBy { it.rank }.maxBy { it.value.size }.key
                Play(cards, HandType.FULL_HOUSE, main)
            }
            isFlush -> Play(cards, HandType.FLUSH5, cards.maxBy { it.rank.order }.rank, cards.maxBy { it.suit.order }.suit)
            isStraight -> Play(cards, HandType.STRAIGHT, straightHighRank!!)
            else -> null
        }
    }

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
