package com.scut.chudadi.controller

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameConfig
import com.scut.chudadi.model.GameState
import com.scut.chudadi.model.PlayerState
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.ScoringMode
import com.scut.chudadi.model.Suit
import com.scut.chudadi.rule.HandEvaluator
import com.scut.chudadi.rule.RuleEngine
import com.scut.chudadi.rule.RuleProfile
import com.scut.chudadi.rule.RuleProfiles
import kotlin.random.Random

class GameController(private val config: GameConfig, players: List<PlayerState>) {
    val state = GameState(players = players)
    val ruleProfile: RuleProfile = RuleProfiles.from(config.ruleSetType)

    fun startGame(seed: Long = System.currentTimeMillis()) {
        state.roundSeed = seed
        val deck = buildDeck().shuffled(Random(seed))
        state.players.forEachIndexed { index, player ->
            player.handCards.clear()
            player.handCards.addAll(deck.subList(index * 13, (index + 1) * 13).sorted())
        }

        state.currentPlayerIndex = ruleProfile.selectFirstPlayer(state.players, state.lastWinnerId)
        state.lastPlay = null
        state.lastPlayPlayerId = null
        state.passCount = 0
        state.firstRound = true
        state.finishOrder.clear()
    }

    fun playCards(playerId: String, cards: List<Card>): Boolean {
        if (isRoundComplete()) return false
        val currentPlayer = state.players[state.currentPlayerIndex]
        if (currentPlayer.id != playerId) return false
        if (!RuleEngine.canPlay(state, currentPlayer.handCards, cards, ruleProfile)) return false

        val play = HandEvaluator.evaluate(cards, ruleProfile) ?: return false
        currentPlayer.handCards.removeAll(cards.toSet())
        state.lastPlay = play
        state.lastPlayPlayerId = currentPlayer.id
        state.passCount = 0
        state.firstRound = false

        if (currentPlayer.handCards.isEmpty()) {
            markFinished(currentPlayer)
            if (state.lastWinnerId == null) {
                state.lastWinnerId = currentPlayer.id
            }
        }

        if (!isRoundComplete()) {
            nextTurn()
        }
        return true
    }

    fun pass(playerId: String): Boolean {
        if (isRoundComplete()) return false
        val currentPlayer = state.players[state.currentPlayerIndex]
        if (currentPlayer.id != playerId) return false
        if (!RuleEngine.canPass(state)) return false

        state.passCount += 1
        if (state.passCount >= passesRequiredToClearTable()) {
            state.lastPlay = null
            state.lastPlayPlayerId = null
            state.passCount = 0
        }

        nextTurn()
        return true
    }

    fun settleRound(): Map<String, Int> {
        val order = completedFinishOrder()
        if (config.scoringMode == ScoringMode.WIN_COUNT) {
            return state.players.associate { player ->
                player.id to if (player.id == order.firstOrNull()) 1 else 0
            }
        }

        val rankScore = state.players.size - 1
        val scoreMap = mutableMapOf<String, Int>()
        order.forEachIndexed { index, playerId ->
            scoreMap[playerId] = rankScore - (index * 2)
        }
        state.lastWinnerId = order.firstOrNull()
        return scoreMap
    }

    private fun nextTurn() {
        val activeIds = activePlayerIds()
        if (activeIds.isEmpty()) return

        var next = (state.currentPlayerIndex + 1) % state.players.size
        repeat(state.players.size) {
            if (state.players[next].id in activeIds) {
                state.currentPlayerIndex = next
                return
            }
            next = (next + 1) % state.players.size
        }
    }

    fun isRoundComplete(): Boolean = state.finishOrder.isNotEmpty()

    private fun markFinished(player: PlayerState) {
        if (player.id !in state.finishOrder) {
            state.finishOrder.add(player.id)
        }
    }

    private fun completedFinishOrder(): List<String> {
        val finishedIds = state.finishOrder.distinct()
        val seatOrder = state.players.mapIndexed { index, player -> player.id to index }.toMap()
        val unfinishedIds = state.players
            .filter { it.id !in finishedIds }
            .sortedWith(
                compareBy<PlayerState> { it.handCards.size }
                    .thenBy { seatOrder[it.id] ?: Int.MAX_VALUE }
            )
            .map { it.id }

        return finishedIds + unfinishedIds
    }

    private fun activePlayerIds(): Set<String> {
        return state.players
            .filter { it.id !in state.finishOrder }
            .map { it.id }
            .toSet()
    }

    private fun activePlayerCount(): Int = activePlayerIds().size

    private fun passesRequiredToClearTable(): Int {
        val activeCount = activePlayerCount()
        val lastPlayOwnerCanRespond = state.lastPlayPlayerId != null &&
            state.lastPlayPlayerId !in state.finishOrder
        return if (lastPlayOwnerCanRespond) {
            (activeCount - 1).coerceAtLeast(1)
        } else {
            activeCount.coerceAtLeast(1)
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
