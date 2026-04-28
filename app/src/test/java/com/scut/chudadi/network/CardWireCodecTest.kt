package com.scut.chudadi.network

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardWireCodecTest {
    @Test
    fun `card should round trip through wire value`() {
        val card = Card(Rank.ACE, Suit.HEART)

        assertEquals(card, CardWireCodec.decode(CardWireCodec.encode(card)))
    }

    @Test
    fun `invalid card should return null`() {
        assertNull(CardWireCodec.decode("bad-card"))
    }
}
