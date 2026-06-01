package com.scut.chudadi.rule

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.PlayerState
import com.scut.chudadi.model.RuleSetType

interface RuleProfile {
    val type: RuleSetType
    val displayName: String
    val firstRoundMustContainDiamondThree: Boolean
    val allowA2345Straight: Boolean
    val compareSuitWhenMajorRankSame: Boolean

    fun selectFirstPlayer(players: List<PlayerState>, lastWinnerId: String?): Int
}

object SouthRuleProfile : RuleProfile {
    override val type: RuleSetType = RuleSetType.SOUTH
    override val displayName: String = "南方规则"
    override val firstRoundMustContainDiamondThree: Boolean = true
    override val allowA2345Straight: Boolean = true
    override val compareSuitWhenMajorRankSame: Boolean = true

    override fun selectFirstPlayer(players: List<PlayerState>, lastWinnerId: String?): Int {
        return players.indexOfFirst { it.handCards.contains(Card.DIAMOND_THREE) }
            .takeIf { it >= 0 }
            ?: 0
    }
}

object NorthRuleProfile : RuleProfile {
    override val type: RuleSetType = RuleSetType.NORTH
    override val displayName: String = "北方规则"
    override val firstRoundMustContainDiamondThree: Boolean = false
    override val allowA2345Straight: Boolean = false
    override val compareSuitWhenMajorRankSame: Boolean = false

    override fun selectFirstPlayer(players: List<PlayerState>, lastWinnerId: String?): Int {
        if (lastWinnerId == null) return 0
        return players.indexOfFirst { it.id == lastWinnerId }
            .takeIf { it >= 0 }
            ?: 0
    }
}

object RuleProfiles {
    fun from(type: RuleSetType): RuleProfile {
        return when (type) {
            RuleSetType.SOUTH -> SouthRuleProfile
            RuleSetType.NORTH -> NorthRuleProfile
        }
    }
}
