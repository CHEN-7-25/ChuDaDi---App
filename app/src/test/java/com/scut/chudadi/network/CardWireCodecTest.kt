package com.scut.chudadi.network

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** CardWireCodec 的牌面网络编码测试。 */
class CardWireCodecTest {
    /** 合法牌面应能编码后再解码回原对象。 */
    @Test
    fun `card should round trip through wire value`() {
        val card = Card(Rank.ACE, Suit.HEART)

        assertEquals(card, CardWireCodec.decode(CardWireCodec.encode(card)))
    }

    /** 非法牌面字符串应返回 null，方便上层拒绝坏消息。 */
    @Test
    fun `invalid card should return null`() {
        assertNull(CardWireCodec.decode("bad-card"))
    }
}
