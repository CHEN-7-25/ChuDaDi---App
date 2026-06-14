package com.scut.chudadi.network

import com.scut.chudadi.model.Card
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.Suit

/** 牌面在蓝牙消息中的轻量字符串编解码器。 */
object CardWireCodec {
    /** 使用 SUIT-RANK 格式传输，避免 UI 文案变化影响协议。 */
    fun encode(card: Card): String = "${card.suit.name}-${card.rank.name}"

    /** 解码失败返回 null，由上层拒绝非法蓝牙动作或非法快照。 */
    fun decode(value: String): Card? {
        val parts = value.split("-")
        if (parts.size != 2) return null

        return runCatching {
            Card(rank = Rank.valueOf(parts[1]), suit = Suit.valueOf(parts[0]))
        }.getOrNull()
    }

    /** 将一组牌转成网络字段列表。 */
    fun encodeList(cards: List<Card>): List<String> = cards.map(::encode)

    /** 任意一张牌解码失败时整组返回 null，避免半合法手牌进入控制器。 */
    fun decodeList(values: List<String>): List<Card>? {
        return values.map { decode(it) ?: return null }
    }
}
