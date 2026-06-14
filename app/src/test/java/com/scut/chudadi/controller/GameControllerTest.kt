package com.scut.chudadi.controller

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameConfig
import com.scut.chudadi.model.PlayerState
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.RuleSetType
import com.scut.chudadi.model.ScoringMode
import com.scut.chudadi.model.Suit
import com.scut.chudadi.rule.NorthRuleProfile
import com.scut.chudadi.rule.SouthRuleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** GameController 的状态推进和结算规则回归测试。 */
class GameControllerTest {

    /** 创建四个空手牌玩家，测试按需手动塞牌以减少发牌随机性影响。 */
    private fun players(): List<PlayerState> {
        return listOf(
            PlayerState("p1", "玩家1", false),
            PlayerState("p2", "玩家2", false),
            PlayerState("p3", "玩家3", false),
            PlayerState("p4", "玩家4", false)
        )
    }

    /** 南方规则必须由持有方块 3 的玩家先手。 */
    @Test
    fun `south first player should be diamond three owner`() {
        val p = players().toMutableList()
        p[2].handCards.add(Card.DIAMOND_THREE)

        val first = SouthRuleProfile.selectFirstPlayer(p, null)

        assertEquals(2, first)
    }

    /** 北方规则在有上局赢家时应由赢家先手。 */
    @Test
    fun `north first player should be last winner when available`() {
        val p = players()

        val first = NorthRuleProfile.selectFirstPlayer(p, "p3")

        assertEquals(2, first)
    }

    /** SCORE 模式按完整名次给 +3 / +1 / -1 / -3。 */
    @Test
    fun `score mode should calculate points by full finish order`() {
        val controller = GameController(
            config = GameConfig(scoringMode = ScoringMode.SCORE, ruleSetType = RuleSetType.SOUTH),
            players = players()
        )

        controller.state.finishOrder.addAll(listOf("p1", "p3", "p2", "p4"))

        val result = controller.settleRound()

        assertEquals(3, result["p1"])
        assertEquals(1, result["p3"])
        assertEquals(-1, result["p2"])
        assertEquals(-3, result["p4"])
        assertTrue(controller.state.lastWinnerId == "p1")
    }

    /** 当前实现一名玩家出完即结束本局。 */
    @Test
    fun `round should complete after first player finishes`() {
        val controller = GameController(
            config = GameConfig(scoringMode = ScoringMode.SCORE, ruleSetType = RuleSetType.NORTH),
            players = players()
        )
        controller.state.players[0].handCards.add(Card(Rank.THREE, Suit.DIAMOND))
        controller.state.players[1].handCards.add(Card(Rank.FOUR, Suit.DIAMOND))
        controller.state.players[2].handCards.add(Card(Rank.FIVE, Suit.DIAMOND))
        controller.state.players[3].handCards.add(Card(Rank.SIX, Suit.DIAMOND))
        controller.state.currentPlayerIndex = 0

        assertTrue(controller.playCards("p1", listOf(Card(Rank.THREE, Suit.DIAMOND))))

        assertEquals(listOf("p1"), controller.state.finishOrder)
        assertEquals(0, controller.state.currentPlayerIndex)
        assertTrue(controller.isRoundComplete())
    }

    /** 本局结束后其他玩家不能继续过牌或出牌。 */
    @Test
    fun `remaining players cannot act after first player finishes`() {
        val controller = GameController(
            config = GameConfig(scoringMode = ScoringMode.SCORE, ruleSetType = RuleSetType.NORTH),
            players = players()
        )
        controller.state.players[0].handCards.add(Card(Rank.THREE, Suit.DIAMOND))
        controller.state.players[1].handCards.add(Card(Rank.FOUR, Suit.DIAMOND))
        controller.state.players[2].handCards.add(Card(Rank.FIVE, Suit.DIAMOND))
        controller.state.players[3].handCards.add(Card(Rank.SIX, Suit.DIAMOND))
        controller.state.currentPlayerIndex = 0

        assertTrue(controller.playCards("p1", listOf(Card(Rank.THREE, Suit.DIAMOND))))

        assertFalse(controller.pass("p2"))
        assertEquals(0, controller.state.passCount)
        assertEquals(0, controller.state.currentPlayerIndex)
        assertTrue(controller.state.lastPlay != null)
    }

    /** 结算时未出完玩家按剩余手牌从少到多补齐名次。 */
    @Test
    fun `settlement should rank unfinished players by remaining cards`() {
        val controller = GameController(
            config = GameConfig(scoringMode = ScoringMode.SCORE, ruleSetType = RuleSetType.NORTH),
            players = players()
        )
        controller.state.players[0].handCards.add(Card(Rank.THREE, Suit.DIAMOND))
        controller.state.players[1].handCards.addAll(
            listOf(
                Card(Rank.FOUR, Suit.DIAMOND),
                Card(Rank.FIVE, Suit.CLUB),
                Card(Rank.SIX, Suit.SPADE)
            )
        )
        controller.state.players[2].handCards.add(Card(Rank.FIVE, Suit.DIAMOND))
        controller.state.players[3].handCards.addAll(
            listOf(
                Card(Rank.SIX, Suit.DIAMOND),
                Card(Rank.SEVEN, Suit.CLUB)
            )
        )
        controller.state.currentPlayerIndex = 0

        assertTrue(controller.playCards("p1", listOf(Card(Rank.THREE, Suit.DIAMOND))))

        val result = controller.settleRound()

        assertEquals(listOf("p1"), controller.state.finishOrder)
        assertEquals(3, controller.state.players[1].handCards.size)
        assertEquals(3, result["p1"])
        assertEquals(1, result["p3"])
        assertEquals(-1, result["p4"])
        assertEquals(-3, result["p2"])
        assertTrue(controller.isRoundComplete())
    }

    /** 相同 seed 必须发出相同手牌，保证蓝牙多端可复现牌局。 */
    @Test
    fun `same seed should deal same hands`() {
        val first = GameController(
            config = GameConfig(scoringMode = ScoringMode.SCORE, ruleSetType = RuleSetType.SOUTH),
            players = players()
        )
        val second = GameController(
            config = GameConfig(scoringMode = ScoringMode.SCORE, ruleSetType = RuleSetType.SOUTH),
            players = players()
        )

        first.startGame(seed = 20260427L)
        second.startGame(seed = 20260427L)

        assertEquals(
            first.state.players.map { it.handCards.toList() },
            second.state.players.map { it.handCards.toList() }
        )
        assertEquals(20260427L, first.state.roundSeed)
    }

    /** 北方规则开新局时应保留上局赢家作为先手。 */
    @Test
    fun `north seeded game should keep last winner as first player`() {
        val controller = GameController(
            config = GameConfig(scoringMode = ScoringMode.SCORE, ruleSetType = RuleSetType.NORTH),
            players = players()
        )

        controller.state.lastWinnerId = "p4"
        controller.startGame(seed = 1L)

        assertEquals(3, controller.state.currentPlayerIndex)
    }

}
