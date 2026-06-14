package com.scut.chudadi.network

import com.scut.chudadi.model.RuleSetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 蓝牙消息文本协议的往返和兼容性测试。 */
class BluetoothMessageCodecTest {
    /** URL 编码应保留中文昵称。 */
    @Test
    fun `join room should preserve chinese player name`() {
        val message = BluetoothMessage.JoinRoom(playerId = "p1", playerName = "玩家一")

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    /** 出牌消息需要完整保留牌面列表。 */
    @Test
    fun `play cards should preserve card list`() {
        val message = BluetoothMessage.PlayCards(
            playerId = "p2",
            cards = listOf("DIAMOND-THREE", "SPADE-TWO")
        )

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    /** 私人手牌消息必须保留接收者和牌列表。 */
    @Test
    fun `private hand should preserve owner and cards`() {
        val message = BluetoothMessage.PrivateHand(
            playerId = "p3",
            cards = listOf("HEART-ACE", "CLUB-KING", "DIAMOND-THREE")
        )

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    /** 座位分配要保留请求 id、正式座位和当前玩家列表。 */
    @Test
    fun `seat assignment should preserve requested and assigned players`() {
        val message = BluetoothMessage.SeatAssigned(
            requestPlayerId = "guest-1",
            assignedPlayerId = "p2",
            players = listOf("p1", "p2")
        )

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    /** 房间状态要同步玩家、准备状态、真人座位和规则。 */
    @Test
    fun `room state should preserve players and ready players`() {
        val message = BluetoothMessage.RoomState(
            players = listOf("p1", "p2", "p3"),
            readyPlayers = listOf("p1", "p3"),
            bluetoothPlayers = listOf("p2", "p3"),
            ruleSetType = RuleSetType.NORTH
        )

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    /** 兼容旧版 ROOM 消息：缺少准备和规则字段时使用默认值。 */
    @Test
    fun `room state should decode legacy payload without ready players`() {
        val decoded = BluetoothMessageCodec.decode("ROOM|p1%2Cp2")

        assertEquals(BluetoothMessage.RoomState(players = listOf("p1", "p2")), decoded)
    }

    /** 心跳消息应携带发送者座位，便于房主按玩家检测超时。 */
    @Test
    fun `heartbeat should preserve sender player`() {
        val message = BluetoothMessage.Heartbeat(timestamp = 202604280915L, playerId = "p2")

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    /** 兼容旧版只带时间戳的心跳消息。 */
    @Test
    fun `heartbeat should decode legacy timestamp only payload`() {
        val decoded = BluetoothMessageCodec.decode("HEARTBEAT|202604280915")

        assertEquals(BluetoothMessage.Heartbeat(timestamp = 202604280915L), decoded)
    }

    /** 开局消息需要保留房主选择的规则。 */
    @Test
    fun `start game should preserve selected rule set`() {
        val message = BluetoothMessage.StartGame(
            seed = 20260427L,
            ruleSetType = RuleSetType.NORTH
        )

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    /** 兼容旧版 START 消息：缺少规则时默认南方规则。 */
    @Test
    fun `start game should decode legacy seed only payload as south rule`() {
        val decoded = BluetoothMessageCodec.decode("START|20260427")

        assertEquals(BluetoothMessage.StartGame(seed = 20260427L), decoded)
    }

    /** 快照消息覆盖公开局面、房间元数据和规则同步字段。 */
    @Test
    fun `snapshot should preserve game sync payload`() {
        val message = BluetoothMessage.GameStateSnapshot(
            seed = 20260427L,
            currentPlayerId = "p3",
            lastPlayCards = listOf("HEART-ACE"),
            hands = mapOf(
                "p1" to listOf("DIAMOND-THREE", "CLUB-FOUR"),
                "p2" to listOf("SPADE-TWO")
            ),
            handCounts = mapOf("p1" to 12, "p2" to 8, "p3" to 4, "p4" to 13),
            scores = mapOf("p1" to 10, "p2" to -2, "p3" to 0, "p4" to -8),
            finishOrder = listOf("p2"),
            passCount = 1,
            firstRound = false,
            lastWinnerId = "p4",
            lastPlayPlayerId = "p2",
            players = listOf("p1", "p2"),
            readyPlayers = listOf("p1", "p2"),
            bluetoothPlayers = listOf("p2"),
            ruleSetType = RuleSetType.NORTH
        )

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    /** 兼容旧版 SNAPSHOT 消息：缺少房间元数据时仍能解码。 */
    @Test
    fun `snapshot should decode payload without room metadata`() {
        val decoded = BluetoothMessageCodec.decode(
            "SNAPSHOT|20260427|p3|HEART-ACE|p2:SPADE-TWO|p1:12,p2:8|p1:10,p2:-2|p2|1|false|p4|p2"
        )

        assertEquals(
            BluetoothMessage.GameStateSnapshot(
                seed = 20260427L,
                currentPlayerId = "p3",
                lastPlayCards = listOf("HEART-ACE"),
                hands = mapOf("p2" to listOf("SPADE-TWO")),
                handCounts = mapOf("p1" to 12, "p2" to 8),
                scores = mapOf("p1" to 10, "p2" to -2),
                finishOrder = listOf("p2"),
                passCount = 1,
                firstRound = false,
                lastWinnerId = "p4",
                lastPlayPlayerId = "p2"
            ),
            decoded
        )
    }

    /** 未知消息类型应返回 null，而不是抛异常。 */
    @Test
    fun `invalid message should return null`() {
        assertNull(BluetoothMessageCodec.decode("UNKNOWN|abc"))
    }
}
