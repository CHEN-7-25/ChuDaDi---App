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

/** RuleEngine 合法性和大小比较规则回归测试。 */
class RuleEngineTest {

    /** 创建一个没有上一手的默认局面，便于单独测试规则层。 */
    private fun newState(): GameState {
        val players = listOf(
            PlayerState("p1", "玩家1", false),
            PlayerState("p2", "玩家2", false),
            PlayerState("p3", "玩家3", false),
            PlayerState("p4", "玩家4", false)
        )
        return GameState(players)
    }

    /** 南方规则首轮出牌必须包含方块 3。 */
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

    /** 北方规则首轮不强制携带方块 3。 */
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

    /** 南方规则同点数继续比较花色。 */
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

    /** 北方规则同点数不比较花色，因此结果为平。 */
    @Test
    fun `north does not compare suit when rank equal`() {
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

        val result = RuleEngine.compare(current, previous, NorthRuleProfile)

        assertTrue(result == 0)
    }

    /** 南方规则同点数顺子继续比较最高点上的花色。 */
    @Test
    fun `south compares major suit for equal-rank straights`() {
        val previous = HandEvaluator.evaluate(
            listOf(
                Card(Rank.SEVEN, Suit.DIAMOND),
                Card(Rank.EIGHT, Suit.CLUB),
                Card(Rank.NINE, Suit.DIAMOND),
                Card(Rank.TEN, Suit.SPADE),
                Card(Rank.JACK, Suit.HEART)
            ),
            SouthRuleProfile
        )!!
        val current = HandEvaluator.evaluate(
            listOf(
                Card(Rank.SEVEN, Suit.CLUB),
                Card(Rank.EIGHT, Suit.DIAMOND),
                Card(Rank.NINE, Suit.CLUB),
                Card(Rank.TEN, Suit.HEART),
                Card(Rank.JACK, Suit.SPADE)
            ),
            SouthRuleProfile
        )!!

        val result = RuleEngine.compare(current, previous, SouthRuleProfile)

        assertTrue(result > 0)
    }

    /** 五张牌先比较牌型等级，葫芦应压过普通同花五。 */
    @Test
    fun `five card type level should outrank lower five card type`() {
        val fullHouse = HandEvaluator.evaluate(
            listOf(
                Card(Rank.SIX, Suit.DIAMOND),
                Card(Rank.SIX, Suit.CLUB),
                Card(Rank.SIX, Suit.SPADE),
                Card(Rank.KING, Suit.DIAMOND),
                Card(Rank.KING, Suit.HEART)
            ),
            SouthRuleProfile
        )!!
        val flush = HandEvaluator.evaluate(
            listOf(
                Card(Rank.THREE, Suit.HEART),
                Card(Rank.SEVEN, Suit.HEART),
                Card(Rank.NINE, Suit.HEART),
                Card(Rank.JACK, Suit.HEART),
                Card(Rank.ACE, Suit.HEART)
            ),
            SouthRuleProfile
        )!!

        val result = RuleEngine.compare(fullHouse, flush, SouthRuleProfile)

        assertTrue(result > 0)
    }
}
