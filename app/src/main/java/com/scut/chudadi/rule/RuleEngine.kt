package com.scut.chudadi.rule

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameState
import com.scut.chudadi.model.HandType
import com.scut.chudadi.model.Play

object RuleEngine {
    fun canPlay(
        state: GameState,
        playerCards: List<Card>,
        toPlay: List<Card>,
        profile: RuleProfile
    ): Boolean {
        if (!playerCards.containsAll(toPlay)) return false

        val currentPlay = HandEvaluator.evaluate(toPlay, profile) ?: return false
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

    fun compare(current: Play, previous: Play, profile: RuleProfile): Int {
        if (current.type.cardCount != previous.type.cardCount) {
            return current.type.cardCount - previous.type.cardCount
        }

        if (current.type.cardCount == 5 && current.type != previous.type) {
            return current.type.level - previous.type.level
        }

        val rankDiff = current.majorRank.order - previous.majorRank.order
        if (rankDiff != 0) return rankDiff

        if (!profile.compareSuitWhenMajorRankSame) return 0
        return current.majorSuit.order - previous.majorSuit.order
    }

    fun canPass(state: GameState): Boolean {
        return state.lastPlay != null
    }

    fun isFiveCardType(type: HandType): Boolean = type.cardCount == 5
}
