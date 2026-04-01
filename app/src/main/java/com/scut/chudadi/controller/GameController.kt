package com.scut.chudadi.controller

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameConfig
import com.scut.chudadi.model.GameState
import com.scut.chudadi.model.PlayerState
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.ScoringMode
import com.scut.chudadi.model.Suit
import com.scut.chudadi.rule.HandEvaluator
import com.scut.chudadi.rule.RuleProfile
import com.scut.chudadi.rule.RuleProfiles
import com.scut.chudadi.rule.RuleEngine

class GameController(private val config: GameConfig, players: List<PlayerState>) {
    val state = GameState(players = players)
    val ruleProfile: RuleProfile = RuleProfiles.from(config.ruleSetType)

    fun startGame() {
        val deck = buildDeck().shuffled()
        state.players.forEachIndexed { index, player ->
            player.handCards.clear()
            player.handCards.addAll(deck.subList(index * 13, (index + 1) * 13).sorted())
        }

        state.currentPlayerIndex = ruleProfile.selectFirstPlayer(state.players, state.lastWinnerId)
        state.lastPlay = null
        state.passCount = 0
        state.firstRound = true
        state.finishOrder.clear()
    }

    fun playCards(playerId: String, cards: List<Card>): Boolean {
        val currentPlayer = state.players[state.currentPlayerIndex]
        if (currentPlayer.id != playerId) return false
        if (!RuleEngine.canPlay(state, currentPlayer.handCards, cards, ruleProfile)) return false

        val play = HandEvaluator.evaluate(cards, ruleProfile) ?: return false
        currentPlayer.handCards.removeAll(cards.toSet())
        state.lastPlay = play
        state.passCount = 0
        state.firstRound = false

        if (currentPlayer.handCards.isEmpty()) {
            state.finishOrder.add(currentPlayer.id)
            if (state.lastWinnerId == null) {
                state.lastWinnerId = currentPlayer.id
            }
        }

        nextTurn()
        return true
    }

    fun pass(playerId: String): Boolean {
        val currentPlayer = state.players[state.currentPlayerIndex]
        if (currentPlayer.id != playerId) return false
        if (!RuleEngine.canPass(state)) return false

        state.passCount += 1
        if (state.passCount >= activePlayerCount() - 1) {
            state.lastPlay = null
            state.passCount = 0
        }

        nextTurn()
        return true
    }

    fun settleRound(): Map<String, Int> {
        val remaining = state.players.associate { it.id to it.handCards.size }
        if (config.scoringMode == ScoringMode.WIN_COUNT) return remaining.mapValues { 0 }

        val winnerId = state.finishOrder.firstOrNull() ?: return remaining
        var total = 0
        val lossMap = mutableMapOf<String, Int>()
        remaining.forEach { (id, count) ->
            if (id == winnerId) return@forEach
            val lose = calculateLoseScore(count)
            lossMap[id] = -lose
            total += lose
        }
        lossMap[winnerId] = total
        state.lastWinnerId = winnerId
        return lossMap
    }

    private fun nextTurn() {
        var next = (state.currentPlayerIndex + 1) % state.players.size
        while (state.players[next].id in state.finishOrder) {
            next = (next + 1) % state.players.size
        }
        state.currentPlayerIndex = next
    }

    private fun activePlayerCount(): Int = state.players.size - state.finishOrder.size

    private fun calculateLoseScore(count: Int): Int {
        return when {
            count < 8 -> count
            count < 10 -> count * 2
            count < 13 -> count * 3
            else -> count * 4
        }
    }

    private fun buildDeck(): List<Card> {
        return Suit.entries.flatMap { suit ->
            Rank.entries.map { rank ->
                Card(rank, suit)
            }
        }
    }
}
