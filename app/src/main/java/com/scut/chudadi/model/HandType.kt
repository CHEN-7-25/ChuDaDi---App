package com.scut.chudadi.model

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

data class Play(
    val cards: List<Card>,
    val type: HandType,
    val majorRank: Rank,
    val majorSuit: Suit = Suit.DIAMOND
)
