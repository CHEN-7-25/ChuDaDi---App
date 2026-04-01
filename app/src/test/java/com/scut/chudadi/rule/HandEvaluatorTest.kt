package com.scut.chudadi.rule

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.HandType
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandEvaluatorTest {

    @Test
    fun `south rule should accept A2345 straight`() {
        val cards = listOf(
            Card(Rank.ACE, Suit.SPADE),
            Card(Rank.TWO, Suit.HEART),
            Card(Rank.THREE, Suit.DIAMOND),
            Card(Rank.FOUR, Suit.CLUB),
            Card(Rank.FIVE, Suit.SPADE)
        )

        val play = HandEvaluator.evaluate(cards, SouthRuleProfile)

        assertEquals(HandType.STRAIGHT, play?.type)
        assertEquals(Rank.FIVE, play?.majorRank)
    }

    @Test
    fun `north rule should reject A2345 straight`() {
        val cards = listOf(
            Card(Rank.ACE, Suit.SPADE),
            Card(Rank.TWO, Suit.HEART),
            Card(Rank.THREE, Suit.DIAMOND),
            Card(Rank.FOUR, Suit.CLUB),
            Card(Rank.FIVE, Suit.SPADE)
        )

        val play = HandEvaluator.evaluate(cards, NorthRuleProfile)

        assertNull(play)
    }

    @Test
    fun `should evaluate straight flush correctly`() {
        val cards = listOf(
            Card(Rank.SEVEN, Suit.SPADE),
            Card(Rank.EIGHT, Suit.SPADE),
            Card(Rank.NINE, Suit.SPADE),
            Card(Rank.TEN, Suit.SPADE),
            Card(Rank.JACK, Suit.SPADE)
        )

        val play = HandEvaluator.evaluate(cards, SouthRuleProfile)

        assertEquals(HandType.STRAIGHT_FLUSH, play?.type)
        assertEquals(Rank.JACK, play?.majorRank)
    }
}
