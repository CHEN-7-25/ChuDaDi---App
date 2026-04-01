package com.scut.chudadi.model

enum class Suit(val order: Int) {
    DIAMOND(0),
    CLUB(1),
    HEART(2),
    SPADE(3)
}

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
        val DIAMOND_THREE = Card(Rank.THREE, Suit.DIAMOND)
    }
}
