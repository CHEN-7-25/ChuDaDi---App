package com.scut.chudadi.model

/**
 * 单个座位的运行时状态。
 *
 * handCards 使用 MutableList 是为了让 GameController 在发牌、出牌时就地更新手牌。
 */
data class PlayerState(
    val id: String,
    val name: String,
    val isAi: Boolean,
    val handCards: MutableList<Card> = mutableListOf(),
    var score: Int = 0
)

/** 当前支持的结算模式。课程演示主要使用 SCORE，WIN_COUNT 预留给只统计胜场的玩法。 */
enum class ScoringMode {
    SCORE,
    WIN_COUNT
}

/** 房间选择的规则集类型，会通过蓝牙消息同步给客户端。 */
enum class RuleSetType {
    SOUTH,
    NORTH
}

/** 创建 GameController 时使用的静态配置。 */
data class GameConfig(
    val scoringMode: ScoringMode = ScoringMode.SCORE,
    val playerCount: Int = 4,
    val ruleSetType: RuleSetType = RuleSetType.SOUTH
)

/**
 * 一局牌的权威内存状态。
 *
 * UI、AI、规则校验和蓝牙快照都围绕这个状态读取或同步；真正推进状态的入口在 GameController。
 */
data class GameState(
    val players: List<PlayerState>,
    /** 当前应行动玩家在 players 中的下标。 */
    var currentPlayerIndex: Int = 0,
    /** 桌面上一手牌；为空表示新一轮自由出牌。 */
    var lastPlay: Play? = null,
    /** 上一手牌的出牌者，用于计算需要多少人过牌后清桌。 */
    var lastPlayPlayerId: String? = null,
    /** 当前桌面上一手之后已经连续过牌的人数。 */
    var passCount: Int = 0,
    /** 是否仍是本局首轮，南方规则会要求首轮必须带方块 3。 */
    var firstRound: Boolean = true,
    /** 上局赢家；北方规则用它决定下一局先手。 */
    var lastWinnerId: String? = null,
    /** 本局洗牌种子，蓝牙联机用它复现同一副牌。 */
    var roundSeed: Long = 0L,
    /** 已出完玩家顺序；当前实现一人出完即结束，仍保留列表便于扩展。 */
    val finishOrder: MutableList<String> = mutableListOf()
)
