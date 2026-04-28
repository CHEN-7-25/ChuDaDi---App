package com.scut.chudadi.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothMessageCodecTest {
    @Test
    fun `join room should preserve chinese player name`() {
        val message = BluetoothMessage.JoinRoom(playerId = "p1", playerName = "玩家一")

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    @Test
    fun `play cards should preserve card list`() {
        val message = BluetoothMessage.PlayCards(
            playerId = "p2",
            cards = listOf("DIAMOND-THREE", "SPADE-TWO")
        )

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    @Test
    fun `private hand should preserve owner and cards`() {
        val message = BluetoothMessage.PrivateHand(
            playerId = "p3",
            cards = listOf("HEART-ACE", "CLUB-KING", "DIAMOND-THREE")
        )

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

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

    @Test
    fun `room state should preserve players and ready players`() {
        val message = BluetoothMessage.RoomState(
            players = listOf("p1", "p2", "p3"),
            readyPlayers = listOf("p1", "p3"),
            bluetoothPlayers = listOf("p2", "p3")
        )

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    @Test
    fun `room state should decode legacy payload without ready players`() {
        val decoded = BluetoothMessageCodec.decode("ROOM|p1%2Cp2")

        assertEquals(BluetoothMessage.RoomState(players = listOf("p1", "p2")), decoded)
    }

    @Test
    fun `heartbeat should preserve sender player`() {
        val message = BluetoothMessage.Heartbeat(timestamp = 202604280915L, playerId = "p2")

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    @Test
    fun `heartbeat should decode legacy timestamp only payload`() {
        val decoded = BluetoothMessageCodec.decode("HEARTBEAT|202604280915")

        assertEquals(BluetoothMessage.Heartbeat(timestamp = 202604280915L), decoded)
    }

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
            lastWinnerId = "p4"
        )

        val decoded = BluetoothMessageCodec.decode(BluetoothMessageCodec.encode(message))

        assertEquals(message, decoded)
    }

    @Test
    fun `invalid message should return null`() {
        assertNull(BluetoothMessageCodec.decode("UNKNOWN|abc"))
    }
}
