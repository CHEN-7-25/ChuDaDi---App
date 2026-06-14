package com.scut.chudadi.rule

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.PlayerState
import com.scut.chudadi.model.RuleSetType

/**
 * 规则差异的策略接口。
 *
 * 南方/北方规则只在少数决策点不同，将差异集中在这里可以避免 RuleEngine 到处写分支。
 */
interface RuleProfile {
    val type: RuleSetType
    val displayName: String
    val firstRoundMustContainDiamondThree: Boolean
    val allowA2345Straight: Boolean
    val compareSuitWhenMajorRankSame: Boolean

    fun selectFirstPlayer(players: List<PlayerState>, lastWinnerId: String?): Int
}

/** 南方规则：方块 3 先手、首轮必须带方块 3、A2345 视为最小顺子。 */
object SouthRuleProfile : RuleProfile {
    override val type: RuleSetType = RuleSetType.SOUTH
    override val displayName: String = "南方规则"
    override val firstRoundMustContainDiamondThree: Boolean = true
    override val allowA2345Straight: Boolean = true
    override val compareSuitWhenMajorRankSame: Boolean = true

    /** 找到持有方块 3 的玩家；异常情况下回退到第一个座位，避免开局崩溃。 */
    override fun selectFirstPlayer(players: List<PlayerState>, lastWinnerId: String?): Int {
        return players.indexOfFirst { it.handCards.contains(Card.DIAMOND_THREE) }
            .takeIf { it >= 0 }
            ?: 0
    }
}

/** 北方规则：首局默认 p1，之后由上局赢家先手，同点数不继续比花色。 */
object NorthRuleProfile : RuleProfile {
    override val type: RuleSetType = RuleSetType.NORTH
    override val displayName: String = "北方规则"
    override val firstRoundMustContainDiamondThree: Boolean = false
    override val allowA2345Straight: Boolean = false
    override val compareSuitWhenMajorRankSame: Boolean = false

    /** lastWinnerId 来自上一局结算；找不到时回退到第一个座位。 */
    override fun selectFirstPlayer(players: List<PlayerState>, lastWinnerId: String?): Int {
        if (lastWinnerId == null) return 0
        return players.indexOfFirst { it.id == lastWinnerId }
            .takeIf { it >= 0 }
            ?: 0
    }
}

/** 从房间配置或蓝牙消息中的规则枚举恢复具体规则策略。 */
object RuleProfiles {
    fun from(type: RuleSetType): RuleProfile {
        return when (type) {
            RuleSetType.SOUTH -> SouthRuleProfile
            RuleSetType.NORTH -> NorthRuleProfile
        }
    }
}
