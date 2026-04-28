package com.scut.chudadi.model

data class PlayerState(
    val id: String,
    val name: String,
    val isAi: Boolean,
    val handCards: MutableList<Card> = mutableListOf(),
    var score: Int = 0
)

enum class ScoringMode {
    SCORE,
    WIN_COUNT
}

enum class RuleSetType {
    SOUTH,
    NORTH
}

data class GameConfig(
    val scoringMode: ScoringMode = ScoringMode.SCORE,
    val playerCount: Int = 4,
    val ruleSetType: RuleSetType = RuleSetType.SOUTH
)

data class GameState(
    val players: List<PlayerState>,
    var currentPlayerIndex: Int = 0,
    var lastPlay: Play? = null,
    var passCount: Int = 0,
    var firstRound: Boolean = true,
    var lastWinnerId: String? = null,
    var roundSeed: Long = 0L,
    val finishOrder: MutableList<String> = mutableListOf()
)
