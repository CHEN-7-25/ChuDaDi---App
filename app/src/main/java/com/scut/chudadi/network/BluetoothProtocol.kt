package com.scut.chudadi.network

sealed class BluetoothMessage {
    data class JoinRoom(val playerId: String, val playerName: String) : BluetoothMessage()
    data class SeatAssigned(
        val requestPlayerId: String,
        val assignedPlayerId: String,
        val players: List<String>
    ) : BluetoothMessage()
    data class Ready(val playerId: String) : BluetoothMessage()
    data class RoomState(
        val players: List<String>,
        val readyPlayers: List<String> = emptyList(),
        val bluetoothPlayers: List<String> = emptyList()
    ) : BluetoothMessage()
    data class StartGame(val seed: Long) : BluetoothMessage()
    data class PlayCards(val playerId: String, val cards: List<String>) : BluetoothMessage()
    data class Pass(val playerId: String) : BluetoothMessage()
    data class PrivateHand(val playerId: String, val cards: List<String>) : BluetoothMessage()
    data class GameStateSnapshot(
        val seed: Long,
        val currentPlayerId: String,
        val lastPlayCards: List<String>,
        val hands: Map<String, List<String>>,
        val handCounts: Map<String, Int>,
        val scores: Map<String, Int>,
        val finishOrder: List<String>,
        val passCount: Int,
        val firstRound: Boolean,
        val lastWinnerId: String?
    ) : BluetoothMessage()
    data class RoundResult(val scoreMap: Map<String, Int>) : BluetoothMessage()
    data class PlayerOffline(val playerId: String) : BluetoothMessage()
    data class Reconnect(val playerId: String) : BluetoothMessage()
    data class Heartbeat(val timestamp: Long, val playerId: String = "") : BluetoothMessage()
    data class Error(val reason: String) : BluetoothMessage()
}

enum class BluetoothConnectionState {
    IDLE,
    HOSTING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

data class BluetoothStatus(
    val state: BluetoothConnectionState,
    val detail: String = "",
    val connectedCount: Int = 0
)

data class BluetoothPeer(
    val name: String,
    val address: String
) {
    val displayLabel: String
        get() = if (name.isBlank()) address else "$name  $address"
}

interface GameSyncManager {
    fun hostRoom(roomId: String)
    fun joinRoom(roomId: String)
    fun sendMessage(message: BluetoothMessage)
    fun onMessage(callback: (BluetoothMessage) -> Unit)
    fun onStatus(callback: (BluetoothStatus) -> Unit)
    fun disconnect()
}
