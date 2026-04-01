package com.scut.chudadi.network

sealed class BluetoothMessage {
    data class JoinRoom(val playerId: String, val playerName: String) : BluetoothMessage()
    data class StartGame(val seed: Long) : BluetoothMessage()
    data class PlayCards(val playerId: String, val cards: List<String>) : BluetoothMessage()
    data class Pass(val playerId: String) : BluetoothMessage()
    data class RoundResult(val scoreMap: Map<String, Int>) : BluetoothMessage()
    data class PlayerOffline(val playerId: String) : BluetoothMessage()
}

interface GameSyncManager {
    fun hostRoom(roomId: String)
    fun joinRoom(roomId: String)
    fun sendMessage(message: BluetoothMessage)
    fun onMessage(callback: (BluetoothMessage) -> Unit)
}
