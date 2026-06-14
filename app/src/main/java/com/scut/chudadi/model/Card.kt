package com.scut.chudadi.model

/**
 * 锄大地使用的花色顺序。
 *
 * order 同时承担排序和同点数比较时的大小依据：方块最小，黑桃最大。
 */
enum class Suit(val order: Int) {
    DIAMOND(0),
    CLUB(1),
    HEART(2),
    SPADE(3)
}

/**
 * 锄大地使用 3 到 2 的点数顺序，2 最大。
 *
 * order 与实际游戏大小保持一致，便于排序、牌型主点数比较和网络快照展示复用。
 */
enum class Rank(val order: Int) {
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),
    EIGHT(8),
    NINE(9),
    TEN(10),
    JACK(11),
    QUEEN(12),
    KING(13),
    ACE(14),
    TWO(15)
}

/**
 * 单张牌模型。
 *
 * Comparable 按点数优先、花色其次排序，对应南方规则下同点数继续比花色的基础顺序。
 */
data class Card(val rank: Rank, val suit: Suit) : Comparable<Card> {
    override fun compareTo(other: Card): Int {
        if (rank.order != other.rank.order) {
            return rank.order - other.rank.order
        }
        return suit.order - other.suit.order
    }

    override fun toString(): String {
        return "${suit.name}-${rank.name}"
    }

    companion object {
        /** 首轮南方规则需要检查的最小牌。 */
        val DIAMOND_THREE = Card(Rank.THREE, Suit.DIAMOND)
    }
}
