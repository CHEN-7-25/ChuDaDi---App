package com.scut.chudadi.rule

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.HandType
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Test

/** HandEvaluator 牌型识别和主值计算测试。 */
class HandEvaluatorTest {

    /** 南方规则把 A2345 当作最小顺子，主点数为 5。 */
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

    /** 北方规则不接受 A2345 特例。 */
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

        assertEquals(null, play)
    }

    /** 同花顺应优先于普通顺子和同花五被识别。 */
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

    /** 葫芦比较时以三张部分作为主点数。 */
    @Test
    fun `should evaluate full house by triple rank`() {
        val cards = listOf(
            Card(Rank.QUEEN, Suit.DIAMOND),
            Card(Rank.QUEEN, Suit.HEART),
            Card(Rank.QUEEN, Suit.SPADE),
            Card(Rank.FIVE, Suit.CLUB),
            Card(Rank.FIVE, Suit.SPADE)
        )

        val play = HandEvaluator.evaluate(cards, SouthRuleProfile)

        assertEquals(HandType.FULL_HOUSE, play?.type)
        assertEquals(Rank.QUEEN, play?.majorRank)
        assertEquals(Suit.SPADE, play?.majorSuit)
    }

    /** 铁支比较时以四张部分作为主点数和主花色。 */
    @Test
    fun `should evaluate four plus one by four-card rank`() {
        val cards = listOf(
            Card(Rank.NINE, Suit.DIAMOND),
            Card(Rank.NINE, Suit.CLUB),
            Card(Rank.NINE, Suit.HEART),
            Card(Rank.NINE, Suit.SPADE),
            Card(Rank.KING, Suit.CLUB)
        )

        val play = HandEvaluator.evaluate(cards, SouthRuleProfile)

        assertEquals(HandType.FOUR_PLUS_ONE, play?.type)
        assertEquals(Rank.NINE, play?.majorRank)
        assertEquals(Suit.SPADE, play?.majorSuit)
    }

    /** 同点数顺子需要保留最高点数上的花色，用于南方规则比较。 */
    @Test
    fun `should keep major suit for same-rank straight comparison`() {
        val cards = listOf(
            Card(Rank.SEVEN, Suit.DIAMOND),
            Card(Rank.EIGHT, Suit.CLUB),
            Card(Rank.NINE, Suit.DIAMOND),
            Card(Rank.TEN, Suit.HEART),
            Card(Rank.JACK, Suit.SPADE)
        )

        val play = HandEvaluator.evaluate(cards, SouthRuleProfile)

        assertEquals(HandType.STRAIGHT, play?.type)
        assertEquals(Rank.JACK, play?.majorRank)
        assertEquals(Suit.SPADE, play?.majorSuit)
    }
}
