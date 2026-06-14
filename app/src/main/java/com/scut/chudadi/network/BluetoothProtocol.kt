package com.scut.chudadi.network

import com.scut.chudadi.model.RuleSetType

/**
 * 蓝牙房间内传输的业务消息。
 *
 * 所有消息都会经过 BluetoothMessageCodec 编码成单行文本，再由 RFCOMM socket 发送。
 */
sealed class BluetoothMessage {
    /** 客户端请求加入房间；此时 playerId 可能还是临时 guest id。 */
    data class JoinRoom(val playerId: String, val playerName: String) : BluetoothMessage()
    /** 房主为加入请求分配正式座位，并回传当前已加入玩家列表。 */
    data class SeatAssigned(
        val requestPlayerId: String,
        val assignedPlayerId: String,
        val players: List<String>
    ) : BluetoothMessage()
    /** 玩家确认已经进入房间并准备开局。 */
    data class Ready(val playerId: String) : BluetoothMessage()
    /** 房间公共状态，用于同步座位、准备状态、真人座位和规则选择。 */
    data class RoomState(
        val players: List<String>,
        val readyPlayers: List<String> = emptyList(),
        val bluetoothPlayers: List<String> = emptyList(),
        val ruleSetType: RuleSetType = RuleSetType.SOUTH
    ) : BluetoothMessage()
    /** 房主广播开局种子和规则，客户端据此创建同规则的本地控制器。 */
    data class StartGame(
        val seed: Long,
        val ruleSetType: RuleSetType = RuleSetType.SOUTH
    ) : BluetoothMessage()
    /** 玩家请求或房主确认出牌；cards 使用 CardWireCodec 的字符串格式。 */
    data class PlayCards(val playerId: String, val cards: List<String>) : BluetoothMessage()
    /** 玩家请求或房主确认过牌。 */
    data class Pass(val playerId: String) : BluetoothMessage()
    /** 房主定向发送给某个客户端的私人手牌。 */
    data class PrivateHand(val playerId: String, val cards: List<String>) : BluetoothMessage()
    /**
     * 权威局面快照。
     *
     * 公共广播时 hands 通常为空；定向快照可只填接收者自己的手牌，避免泄露其他玩家手牌。
     */
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
        val lastWinnerId: String?,
        val lastPlayPlayerId: String? = null,
        val players: List<String> = emptyList(),
        val readyPlayers: List<String> = emptyList(),
        val bluetoothPlayers: List<String> = emptyList(),
        val ruleSetType: RuleSetType = RuleSetType.SOUTH
    ) : BluetoothMessage()
    /** 本局结算增量，主要用于日志和客户端展示。 */
    data class RoundResult(val scoreMap: Map<String, Int>) : BluetoothMessage()
    /** 连接断开或心跳超时后广播离线座位。 */
    data class PlayerOffline(val playerId: String) : BluetoothMessage()
    /** 客户端以既有座位身份重新连接。 */
    data class Reconnect(val playerId: String) : BluetoothMessage()
    /** 连接保活消息；playerId 为空时兼容旧格式。 */
    data class Heartbeat(val timestamp: Long, val playerId: String = "") : BluetoothMessage()
    /** 协议或业务校验失败时发送的错误说明。 */
    data class Error(val reason: String) : BluetoothMessage()
}

/** 蓝牙连接状态，供 MainActivity 渲染房间状态和错误提示。 */
enum class BluetoothConnectionState {
    IDLE,
    HOSTING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

/** 连接状态回调的数据载体。 */
data class BluetoothStatus(
    val state: BluetoothConnectionState,
    val detail: String = "",
    val connectedCount: Int = 0
)

/** 已配对设备展示模型。 */
data class BluetoothPeer(
    val name: String,
    val address: String
) {
    /** 列表中展示设备名和地址；无设备名时直接展示地址。 */
    val displayLabel: String
        get() = if (name.isBlank()) address else "$name  $address"
}

/** 蓝牙同步管理器抽象，便于 UI 不直接依赖 socket 细节。 */
interface GameSyncManager {
    fun hostRoom(roomId: String)
    fun joinRoom(roomId: String)
    fun sendMessage(message: BluetoothMessage)
    fun onMessage(callback: (BluetoothMessage) -> Unit)
    fun onStatus(callback: (BluetoothStatus) -> Unit)
    fun disconnect()
}
