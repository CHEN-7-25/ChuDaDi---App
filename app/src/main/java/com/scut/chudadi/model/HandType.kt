package com.scut.chudadi.model

/**
 * 已支持的出牌牌型。
 *
 * cardCount 用于限制只能用相同张数跟牌；level 用于五张牌型之间的强弱比较。
 */
enum class HandType(val cardCount: Int, val level: Int = 0) {
    SINGLE(1),
    PAIR(2),
    TRIPLE(3),
    BOMB4(4),
    STRAIGHT(5, 1),
    FLUSH5(5, 2),
    FULL_HOUSE(5, 3),
    FOUR_PLUS_ONE(5, 4),
    STRAIGHT_FLUSH(5, 5)
}

/**
 * 一次已经识别成功的出牌。
 *
 * majorRank 和 majorSuit 是比较大小时的主值，例如对子取对子点数，葫芦取三张部分点数。
 */
data class Play(
    val cards: List<Card>,
    val type: HandType,
    val majorRank: Rank,
    val majorSuit: Suit = Suit.DIAMOND
)
