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
    fun `score mode should calculate winner and loser points`() {
        val controller = GameController(
            config = GameConfig(scoringMode = ScoringMode.SCORE, ruleSetType = RuleSetType.SOUTH),
            players = players()
        )

        controller.state.players[0].handCards.clear()
        controller.state.players[1].handCards.clear()
        controller.state.players[2].handCards.clear()
        controller.state.players[3].handCards.clear()

        repeat(7) { controller.state.players[1].handCards.add(Card(Rank.THREE, Suit.CLUB)) }
        repeat(8) { controller.state.players[2].handCards.add(Card(Rank.FOUR, Suit.CLUB)) }
        repeat(13) { controller.state.players[3].handCards.add(Card(Rank.FIVE, Suit.CLUB)) }

        controller.state.finishOrder.add("p1")

        val result = controller.settleRound()

        assertEquals(75, result["p1"])
        assertEquals(-7, result["p2"])
        assertEquals(-16, result["p3"])
        assertEquals(-52, result["p4"])
        assertTrue(controller.state.lastWinnerId == "p1")
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
