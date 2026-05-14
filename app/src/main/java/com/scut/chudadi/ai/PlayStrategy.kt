package com.scut.chudadi.ai

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameState

interface PlayStrategy {
    fun chooseCards(state: GameState, handCards: List<Card>): List<Card>?
}
