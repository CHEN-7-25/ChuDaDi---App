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
import org.junit.Assert.assertTrue
import org.junit.Test

class GameControllerTest {

    private fun players(): List<PlayerState> {
        return listOf(
            PlayerState("p1", "玩家1", false),
            PlayerState("p2", "玩家2", false),
            PlayerState("p3", "玩家3", false),
            PlayerState("p4", "玩家4", false)
        )
    }

    @Test
    fun `south first player should be diamond three owner`() {
        val p = players().toMutableList()
        p[2].handCards.add(Card.DIAMOND_THREE)

        val first = SouthRuleProfile.selectFirstPlayer(p, null)

        assertEquals(2, first)
    }

    @Test
    fun `north first player should be last winner when available`() {
        val p = players()

        val first = NorthRuleProfile.selectFirstPlayer(p, "p3")

        assertEquals(2, first)
    }

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

    @Test
    fun `round should continue after first player finishes`() {
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
        assertEquals(1, controller.state.currentPlayerIndex)
        assertTrue(!controller.isRoundComplete())
    }

    @Test
    fun `all remaining players must respond before clearing finished player's last play`() {
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
        assertTrue(controller.pass("p2"))
        assertTrue(controller.pass("p3"))

        assertEquals(2, controller.state.passCount)
        assertEquals(3, controller.state.currentPlayerIndex)
        assertTrue(controller.state.lastPlay != null)

        assertTrue(controller.pass("p4"))

        assertEquals(0, controller.state.passCount)
        assertEquals(1, controller.state.currentPlayerIndex)
        assertEquals(null, controller.state.lastPlay)
    }

    @Test
    fun `last remaining player should be ranked fourth automatically`() {
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
        assertTrue(controller.playCards("p2", listOf(Card(Rank.FOUR, Suit.DIAMOND))))
        assertTrue(controller.playCards("p3", listOf(Card(Rank.FIVE, Suit.DIAMOND))))

        assertEquals(listOf("p1", "p2", "p3", "p4"), controller.state.finishOrder)
        assertEquals(0, controller.state.players[3].handCards.size)
        assertTrue(controller.isRoundComplete())
    }

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
