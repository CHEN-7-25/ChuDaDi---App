package com.scut.chudadi.rule

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.PlayerState

object SouthRuleProfile {
    val displayName: String = "南方规则"

    fun selectFirstPlayer(players: List<PlayerState>): Int {
        return players.indexOfFirst { it.handCards.contains(Card.DIAMOND_THREE) }
            .takeIf { it >= 0 }
            ?: 0
    }
}
