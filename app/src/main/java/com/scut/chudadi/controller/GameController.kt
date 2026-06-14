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

/**
 * 牌局状态推进控制器。
 *
 * Controller 只处理规则层之上的状态变更，不直接依赖 Android UI 或蓝牙连接。
 */
class GameController(private val config: GameConfig, players: List<PlayerState>) {
    /** 当前局面状态，供 UI、AI 和网络快照读取。 */
    val state = GameState(players = players)
    /** 根据配置选择的规则策略，本局内保持稳定。 */
    val ruleProfile: RuleProfile = RuleProfiles.from(config.ruleSetType)

    /** 使用 seed 洗牌并发给四个玩家；相同 seed 可在蓝牙多端复现同一副牌。 */
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

    /**
     * 当前玩家尝试出牌。
     *
     * 返回 false 表示不是当前玩家、牌不合法或本局已结束；成功时会推进到下一名玩家。
     */
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
            // 当前实现一人出完即结束，因此第一个出完者就是下一局北方规则的先手依据。
            if (state.lastWinnerId == null) {
                state.lastWinnerId = currentPlayer.id
            }
        }

        if (!isRoundComplete()) {
            nextTurn()
        }
        return true
    }

    /**
     * 当前玩家过牌。
     *
     * 当需要响应上一手的玩家都过牌后，会清空桌面上一手，让下一名玩家重新自由出牌。
     */
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

    /** 结算本局分数；SCORE 模式按名次给 +3 / +1 / -1 / -3。 */
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

    /** 跳到下一名仍在本局中的玩家，跳过已经出完的座位。 */
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

    /** 当前版本一名玩家出完即结束，因此 finishOrder 非空就表示本局完成。 */
    fun isRoundComplete(): Boolean = state.finishOrder.isNotEmpty()

    /** 防止重复把同一玩家写入完成顺序。 */
    private fun markFinished(player: PlayerState) {
        if (player.id !in state.finishOrder) {
            state.finishOrder.add(player.id)
        }
    }

    /**
     * 补齐完整名次。
     *
     * 已出完玩家排前面；未出完玩家按剩余手牌少到多排序，数量相同按座位顺序稳定排序。
     */
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

    /** 仍需要参与出牌或响应的玩家集合。 */
    private fun activePlayerIds(): Set<String> {
        return state.players
            .filter { it.id !in state.finishOrder }
            .map { it.id }
            .toSet()
    }

    /** 剩余活跃玩家数量，主要用于判断多少次过牌后清桌。 */
    private fun activePlayerCount(): Int = activePlayerIds().size

    /** 根据上一手出牌者是否仍在局内，计算清桌前需要的过牌次数。 */
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

    /** 构造标准 52 张扑克牌。 */
    private fun buildDeck(): List<Card> {
        return Suit.entries.flatMap { suit ->
            Rank.entries.map { rank ->
                Card(rank, suit)
            }
        }
    }
}
