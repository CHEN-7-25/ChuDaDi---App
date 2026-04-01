package com.scut.chudadi.rule

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameState
import com.scut.chudadi.model.HandType
import com.scut.chudadi.model.Play
import com.scut.chudadi.model.PlayerState
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.Suit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private fun newState(): GameState {
        val players = listOf(
            PlayerState("p1", "玩家1", false),
            PlayerState("p2", "玩家2", false),
            PlayerState("p3", "玩家3", false),
            PlayerState("p4", "玩家4", false)
        )
        return GameState(players)
    }

    @Test
    fun `south first round must contain diamond three`() {
        val state = newState().apply { firstRound = true }
        val hand = listOf(Card(Rank.FIVE, Suit.SPADE), Card.DIAMOND_THREE)

        val canPlayWithoutDiamond3 = RuleEngine.canPlay(
            state = state,
            playerCards = hand,
            toPlay = listOf(Card(Rank.FIVE, Suit.SPADE)),
            profile = SouthRuleProfile
        )

        assertFalse(canPlayWithoutDiamond3)
    }

    @Test
    fun `north first round can play without diamond three`() {
        val state = newState().apply { firstRound = true }
        val hand = listOf(Card(Rank.FIVE, Suit.SPADE), Card.DIAMOND_THREE)

        val canPlay = RuleEngine.canPlay(
            state = state,
            playerCards = hand,
            toPlay = listOf(Card(Rank.FIVE, Suit.SPADE)),
            profile = NorthRuleProfile
        )

        assertTrue(canPlay)
    }

    @Test
    fun `south compares suit when rank equal`() {
        val previous = Play(
            cards = listOf(Card(Rank.NINE, Suit.HEART)),
            type = HandType.SINGLE,
            majorRank = Rank.NINE,
            majorSuit = Suit.HEART
        )
        val current = Play(
            cards = listOf(Card(Rank.NINE, Suit.SPADE)),
            type = HandType.SINGLE,
            majorRank = Rank.NINE,
            majorSuit = Suit.SPADE
        )

        val result = RuleEngine.compare(current, previous, SouthRuleProfile)

        assertTrue(result > 0)
    }
}
