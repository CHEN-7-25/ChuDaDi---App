package com.scut.chudadi.network

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.Suit

object CardWireCodec {
    fun encode(card: Card): String = "${card.suit.name}-${card.rank.name}"

    fun decode(value: String): Card? {
        val parts = value.split("-")
        if (parts.size != 2) return null

        return runCatching {
            Card(rank = Rank.valueOf(parts[1]), suit = Suit.valueOf(parts[0]))
        }.getOrNull()
    }

    fun encodeList(cards: List<Card>): List<String> = cards.map(::encode)

    fun decodeList(values: List<String>): List<Card>? {
        return values.map { decode(it) ?: return null }
    }
}
