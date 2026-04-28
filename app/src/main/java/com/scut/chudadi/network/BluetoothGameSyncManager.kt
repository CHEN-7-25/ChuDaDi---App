package com.scut.chudadi.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BluetoothGameSyncManager(
    context: Context,
    private val localPlayerId: String,
    private val maxClientCount: Int = DEFAULT_MAX_CLIENT_COUNT,
    private val serviceUuid: UUID = CHUDADI_SERVICE_UUID
) : GameSyncManager {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val connections = ConcurrentHashMap<String, Connection>()
    private val playerConnectionIds = ConcurrentHashMap<String, String>()
    private var executor: ExecutorService = Executors.newCachedThreadPool()
    private var serverSocket: BluetoothServerSocket? = null
    private var messageCallback: ((BluetoothMessage) -> Unit)? = null
    private var statusCallback: ((BluetoothStatus) -> Unit)? = null
    @Volatile private var closed = false

    override fun onMessage(callback: (BluetoothMessage) -> Unit) {
        messageCallback = callback
    }

    override fun onStatus(callback: (BluetoothStatus) -> Unit) {
        statusCallback = callback
    }

    @SuppressLint("MissingPermission")
    fun bondedPeers(): List<BluetoothPeer> {
        val bluetoothAdapter = adapter ?: return emptyList()
        if (!BluetoothPermissionHelper.hasRequiredPermissions(appContext)) return emptyList()

        return bluetoothAdapter.bondedDevices
            .map { device ->
                BluetoothPeer(
                    name = device.name.orEmpty(),
                    address = device.address
                )
            }
            .sortedWith(compareBy<BluetoothPeer> { it.name.ifBlank { it.address } })
    }

    @SuppressLint("MissingPermission")
    override fun hostRoom(roomId: String) {
        if (!ensureBluetoothReady()) return
        if (!BluetoothPermissionHelper.hasRequiredPermissions(appContext)) {
            emitStatus(BluetoothConnectionState.ERROR, "缺少蓝牙运行时权限")
            return
        }

        closeSockets()
        ensureExecutor()
        closed = false
        emitStatus(BluetoothConnectionState.HOSTING, "房间 $roomId 正在等待连接")

        executor.execute {
            try {
                serverSocket = adapter?.listenUsingRfcommWithServiceRecord(
                    serviceName(roomId),
                    serviceUuid
                )

                while (!closed) {
                    val socket = serverSocket?.accept() ?: break
                    if (connections.size >= maxClientCount) {
                        runCatching { socket.close() }
                        emitStatus(
                            BluetoothConnectionState.ERROR,
                            "房间已满，拒绝新的蓝牙连接",
                            connections.size
                        )
                    } else {
                        registerConnection(socket)
                    }
                }
            } catch (error: IOException) {
                if (!closed) {
                    emitStatus(BluetoothConnectionState.ERROR, error.message.orEmpty())
                }
            } finally {
                closeServerSocket()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun joinRoom(roomId: String) {
        connectToRoom(roomId, BluetoothMessage.JoinRoom(localPlayerId, localPlayerId))
    }

    fun reconnectRoom(roomId: String, playerId: String) {
        connectToRoom(roomId, BluetoothMessage.Reconnect(playerId))
    }

    @SuppressLint("MissingPermission")
    private fun connectToRoom(roomId: String, firstMessage: BluetoothMessage) {
        if (!ensureBluetoothReady()) return
        if (!BluetoothPermissionHelper.hasRequiredPermissions(appContext)) {
            emitStatus(BluetoothConnectionState.ERROR, "缺少蓝牙运行时权限")
            return
        }

        closeSockets()
        ensureExecutor()
        closed = false
        emitStatus(BluetoothConnectionState.CONNECTING, "正在连接 $roomId")

        executor.execute {
            try {
                adapter?.cancelDiscovery()
                val device = findRemoteDevice(roomId)
                val socket = device.createRfcommSocketToServiceRecord(serviceUuid)
                socket.connect()
                registerConnection(socket)
                sendMessage(firstMessage)
            } catch (error: IOException) {
                emitStatus(BluetoothConnectionState.ERROR, error.message.orEmpty())
                disconnect()
            } catch (error: IllegalArgumentException) {
                emitStatus(BluetoothConnectionState.ERROR, error.message.orEmpty())
                disconnect()
            }
        }
    }

    override fun sendMessage(message: BluetoothMessage) {
        val line = BluetoothMessageCodec.encode(message)
        val failedIds = mutableListOf<String>()

        connections.forEach { (connectionId, connection) ->
            runCatching { connection.write(line) }
                .onFailure { failedIds.add(connectionId) }
        }

        failedIds.forEach { connectionId ->
            closeConnection(connectionId, notifyOffline = true)
        }
    }

    fun sendMessageToPlayer(playerId: String, message: BluetoothMessage): Boolean {
        val connectionId = playerConnectionIds[playerId] ?: return false
        val connection = connections[connectionId] ?: return false
        return runCatching {
            connection.write(BluetoothMessageCodec.encode(message))
        }.onFailure {
            closeConnection(connectionId, notifyOffline = true)
        }.isSuccess
    }

    fun bindPlayerAlias(existingPlayerId: String, aliasPlayerId: String): Boolean {
        val connectionId = playerConnectionIds[existingPlayerId] ?: return false
        playerConnectionIds[aliasPlayerId] = connectionId
        return true
    }

    override fun disconnect() {
        closed = true
        closeSockets()
        executor.shutdownNow()
        emitStatus(BluetoothConnectionState.DISCONNECTED, "蓝牙连接已断开")
    }

    @SuppressLint("MissingPermission")
    private fun registerConnection(socket: BluetoothSocket) {
        val connectionId = socket.remoteDevice?.address ?: socket.hashCode().toString()
        val connection = Connection(socket)
        connections[connectionId] = connection
        emitStatus(
            BluetoothConnectionState.CONNECTED,
            "已连接 ${socket.remoteDevice?.name ?: connectionId}",
            connections.size
        )

        executor.execute {
            readLoop(connectionId, connection)
        }
    }

    private fun readLoop(connectionId: String, connection: Connection) {
        try {
            connection.reader.use { reader ->
                while (!closed) {
                    val line = reader.readLine() ?: break
                    val message = BluetoothMessageCodec.decode(line)
                    if (message == null) {
                        emitMessage(BluetoothMessage.Error("无法解析蓝牙消息：$line"))
                    } else {
                        bindPlayerFromMessage(connectionId, message)
                        emitMessage(message)
                    }
                }
            }
        } catch (error: IOException) {
            if (!closed) {
                emitMessage(BluetoothMessage.Error(error.message.orEmpty()))
            }
        } finally {
            closeConnection(connectionId, notifyOffline = !closed)
        }
    }

    @SuppressLint("MissingPermission")
    private fun findRemoteDevice(roomId: String): BluetoothDevice {
        val bluetoothAdapter = adapter ?: throw IllegalArgumentException("设备不支持蓝牙")
        if (BluetoothAdapter.checkBluetoothAddress(roomId)) {
            return bluetoothAdapter.getRemoteDevice(roomId)
        }

        return bluetoothAdapter.bondedDevices.firstOrNull { device ->
            device.address == roomId || device.name == roomId
        } ?: throw IllegalArgumentException("没有找到已配对设备：$roomId")
    }

    private fun ensureBluetoothReady(): Boolean {
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) {
            emitStatus(BluetoothConnectionState.ERROR, "设备不支持蓝牙")
            return false
        }
        if (!bluetoothAdapter.isEnabled) {
            emitStatus(BluetoothConnectionState.ERROR, "请先打开系统蓝牙")
            return false
        }
        return true
    }

    private fun ensureExecutor() {
        if (executor.isShutdown || executor.isTerminated) {
            executor = Executors.newCachedThreadPool()
        }
    }

    private fun closeConnection(connectionId: String, notifyOffline: Boolean) {
        val connection = connections.remove(connectionId) ?: return
        val offlinePlayerId = offlinePlayerIdFor(connectionId)
        playerConnectionIds.entries
            .filter { it.value == connectionId }
            .map { it.key }
            .forEach { playerConnectionIds.remove(it) }
        connection.close()
        if (notifyOffline) {
            emitMessage(BluetoothMessage.PlayerOffline(offlinePlayerId))
        }
        emitStatus(
            BluetoothConnectionState.DISCONNECTED,
            "连接已断开：$connectionId",
            connections.size
        )
    }

    private fun offlinePlayerIdFor(connectionId: String): String {
        val aliases = playerConnectionIds.entries
            .filter { it.value == connectionId }
            .map { it.key }

        return aliases.firstOrNull { FORMAL_PLAYER_ID.matches(it) }
            ?: aliases.firstOrNull { !it.startsWith("guest-") }
            ?: aliases.firstOrNull()
            ?: connectionId
    }

    private fun closeSockets() {
        closeServerSocket()
        connections.keys.toList().forEach { closeConnection(it, notifyOffline = false) }
        playerConnectionIds.clear()
    }

    private fun closeServerSocket() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun emitMessage(message: BluetoothMessage) {
        val callback = messageCallback ?: return
        mainHandler.post { callback(message) }
    }

    private fun emitStatus(
        state: BluetoothConnectionState,
        detail: String,
        connectedCount: Int = connections.size
    ) {
        val callback = statusCallback ?: return
        mainHandler.post {
            callback(BluetoothStatus(state, detail, connectedCount))
        }
    }

    private fun serviceName(roomId: String): String = "ChuDaDi-$roomId"

    private fun bindPlayerFromMessage(connectionId: String, message: BluetoothMessage) {
        val playerId = when (message) {
            is BluetoothMessage.JoinRoom -> message.playerId
            is BluetoothMessage.Ready -> message.playerId
            is BluetoothMessage.PlayCards -> message.playerId
            is BluetoothMessage.Pass -> message.playerId
            is BluetoothMessage.Reconnect -> message.playerId
            is BluetoothMessage.Heartbeat -> message.playerId
            else -> null
        }
        if (!playerId.isNullOrEmpty()) {
            playerConnectionIds[playerId] = connectionId
        }
    }

    private class Connection(socket: BluetoothSocket) {
        val reader = BufferedReader(
            InputStreamReader(socket.inputStream, StandardCharsets.UTF_8)
        )
        private val writer = BufferedWriter(
            OutputStreamWriter(socket.outputStream, StandardCharsets.UTF_8)
        )
        private val socketRef = socket

        @Synchronized
        fun write(line: String) {
            writer.write(line)
            writer.newLine()
            writer.flush()
        }

        fun close() {
            runCatching { writer.close() }
            runCatching { reader.close() }
            runCatching { socketRef.close() }
        }
    }

    companion object {
        val CHUDADI_SERVICE_UUID: UUID = UUID.fromString("a9695c24-48b7-4c71-a4fb-f1056c97f751")
        private val FORMAL_PLAYER_ID = Regex("p[1-4]")
        private const val DEFAULT_MAX_CLIENT_COUNT = 3
    }
}
