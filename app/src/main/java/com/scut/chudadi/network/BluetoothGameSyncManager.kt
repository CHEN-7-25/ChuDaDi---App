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

/**
 * 基于经典蓝牙 RFCOMM 的房间同步管理器。
 *
 * 该类只负责连接、收发和连接状态回调；出牌校验、座位分配和快照应用由 MainActivity 处理。
 */
class BluetoothGameSyncManager(
    context: Context,
    private val localPlayerId: String,
    private val maxClientCount: Int = DEFAULT_MAX_CLIENT_COUNT,
    private val serviceUuid: UUID = CHUDADI_SERVICE_UUID
) : GameSyncManager {
    /** 使用 applicationContext 避免 Activity 重建后持有旧页面引用。 */
    private val appContext = context.applicationContext
    /** 所有回调都切回主线程，方便 UI 层直接渲染。 */
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    /** connectionId 通常是远端 MAC 地址，保存每条 socket 连接。 */
    private val connections = ConcurrentHashMap<String, Connection>()
    /** 玩家 id 到 connectionId 的映射，用于定向发送私人手牌。 */
    private val playerConnectionIds = ConcurrentHashMap<String, String>()
    private var executor: ExecutorService = Executors.newCachedThreadPool()
    /** 房主可能同时打开安全和兼容两个监听通道。 */
    private val serverSockets = ConcurrentHashMap<String, BluetoothServerSocket>()
    private var messageCallback: ((BluetoothMessage) -> Unit)? = null
    private var statusCallback: ((BluetoothStatus) -> Unit)? = null
    /** 读写线程共同检查的关闭标记。 */
    @Volatile private var closed = false

    /** 注册消息回调；回调会在主线程执行。 */
    override fun onMessage(callback: (BluetoothMessage) -> Unit) {
        messageCallback = callback
    }

    /** 注册连接状态回调；回调会在主线程执行。 */
    override fun onStatus(callback: (BluetoothStatus) -> Unit) {
        statusCallback = callback
    }

    /** 返回系统已配对设备，客户端加入房间时优先从这里选择主机手机。 */
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

    /** 房主创建房间并开始监听客户端连接。 */
    @SuppressLint("MissingPermission")
    override fun hostRoom(roomId: String) {
        if (!BluetoothPermissionHelper.hasRequiredPermissions(appContext)) {
            emitStatus(BluetoothConnectionState.ERROR, "缺少蓝牙运行时权限")
            return
        }
        if (!ensureBluetoothReady()) return

        closeSockets()
        ensureExecutor()
        closed = false
        emitStatus(BluetoothConnectionState.HOSTING, "房间 $roomId 正在等待连接")

        executor.execute {
            val bluetoothAdapter = adapter
            if (bluetoothAdapter == null) {
                emitStatus(BluetoothConnectionState.ERROR, "设备不支持蓝牙")
                return@execute
            }

            val errors = mutableListOf<String>()
            // 同时尝试安全和兼容通道，提高不同设备/系统版本之间的连接成功率。
            val startedCount = listOf(
                runCatching {
                    startAcceptLoop(
                        channelName = SECURE_CHANNEL_NAME,
                        serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                            serviceName(roomId),
                            serviceUuid
                        )
                    )
                }.onFailure { error ->
                    errors.add("$SECURE_CHANNEL_NAME：${error.message.orEmpty()}")
                }.isSuccess,
                runCatching {
                    startAcceptLoop(
                        channelName = COMPAT_CHANNEL_NAME,
                        serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            "${serviceName(roomId)}-Compat",
                            COMPAT_SERVICE_UUID
                        )
                    )
                }.onFailure { error ->
                    errors.add("$COMPAT_CHANNEL_NAME：${error.message.orEmpty()}")
                }.isSuccess
            ).count { it }

            if (startedCount == 0) {
                emitStatus(
                    BluetoothConnectionState.ERROR,
                    "蓝牙房间监听失败：${errors.joinToString("；")}"
                )
                closeServerSockets()
            } else if (errors.isNotEmpty()) {
                emitStatus(
                    BluetoothConnectionState.HOSTING,
                    "房间正在等待连接，部分通道不可用：${errors.joinToString("；")}",
                    connections.size
                )
            }
        }
    }

    /** 客户端首次加入房间，连接成功后立即发送 JoinRoom。 */
    @SuppressLint("MissingPermission")
    override fun joinRoom(roomId: String) {
        connectToRoom(roomId, BluetoothMessage.JoinRoom(localPlayerId, localPlayerId))
    }

    /** 客户端断线后以已分配座位身份重新连接。 */
    fun reconnectRoom(roomId: String, playerId: String) {
        connectToRoom(roomId, BluetoothMessage.Reconnect(playerId))
    }

    /** 连接主机设备，并在 socket 建立后发送首条握手消息。 */
    @SuppressLint("MissingPermission")
    private fun connectToRoom(roomId: String, firstMessage: BluetoothMessage) {
        if (!BluetoothPermissionHelper.hasRequiredPermissions(appContext)) {
            emitStatus(BluetoothConnectionState.ERROR, "缺少蓝牙运行时权限")
            return
        }
        if (!ensureBluetoothReady()) return

        closeSockets()
        ensureExecutor()
        closed = false
        emitStatus(BluetoothConnectionState.CONNECTING, "正在连接 $roomId")

        executor.execute {
            try {
                // 连接前取消发现流程，避免系统扫描拖慢 RFCOMM 建连。
                if (BluetoothPermissionHelper.hasScanPermission(appContext)) {
                    runCatching { adapter?.cancelDiscovery() }
                }
                val device = findRemoteDevice(roomId)
                val socket = connectSocketWithFallback(device)
                registerConnection(socket)
                sendMessage(firstMessage)
            } catch (error: IOException) {
                failConnection(error.message.orEmpty())
            } catch (error: IllegalArgumentException) {
                failConnection(error.message.orEmpty())
            } catch (error: SecurityException) {
                failConnection("缺少蓝牙连接权限：${error.message.orEmpty()}")
            }
        }
    }

    /** 广播消息到所有已连接 socket；写失败的连接会被关闭并通知上层离线。 */
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

    /** 向指定玩家连接定向发送消息，主要用于私人手牌或定向快照。 */
    fun sendMessageToPlayer(playerId: String, message: BluetoothMessage): Boolean {
        val connectionId = playerConnectionIds[playerId] ?: return false
        val connection = connections[connectionId] ?: return false
        return runCatching {
            connection.write(BluetoothMessageCodec.encode(message))
        }.onFailure {
            closeConnection(connectionId, notifyOffline = true)
        }.isSuccess
    }

    /** 将临时 guest id 与房主分配的正式座位绑定到同一条连接。 */
    fun bindPlayerAlias(existingPlayerId: String, aliasPlayerId: String): Boolean {
        val connectionId = playerConnectionIds[existingPlayerId] ?: return false
        playerConnectionIds[aliasPlayerId] = connectionId
        return true
    }

    /** 主动断开所有 socket 和监听线程。 */
    override fun disconnect() {
        closed = true
        closeSockets()
        executor.shutdownNow()
        emitStatus(BluetoothConnectionState.DISCONNECTED, "蓝牙连接已断开")
    }

    /** 建连失败时统一清理资源并回调错误状态。 */
    private fun failConnection(reason: String) {
        closed = true
        closeSockets()
        executor.shutdownNow()
        emitStatus(
            BluetoothConnectionState.ERROR,
            reason.ifBlank { "蓝牙连接失败，请确认两台手机已系统配对，房主已创建房间，并选择的是房主手机地址。" }
        )
    }

    /** 房主在单个通道上循环 accept 客户端连接。 */
    private fun startAcceptLoop(channelName: String, serverSocket: BluetoothServerSocket) {
        serverSockets[channelName] = serverSocket
        emitStatus(
            BluetoothConnectionState.HOSTING,
            "房间正在等待连接：$channelName",
            connections.size
        )
        executor.execute {
            try {
                while (!closed) {
                    val socket = serverSocket.accept() ?: break
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
                    emitStatus(
                        BluetoothConnectionState.ERROR,
                        "$channelName 监听失败：${error.message.orEmpty()}"
                    )
                }
            } finally {
                serverSockets.remove(channelName)
                runCatching { serverSocket.close() }
            }
        }
    }

    /** 客户端先尝试安全通道，失败后自动回退到兼容通道。 */
    @SuppressLint("MissingPermission")
    private fun connectSocketWithFallback(device: BluetoothDevice): BluetoothSocket {
        val errors = mutableListOf<String>()
        val attempts = listOf(
            SECURE_CHANNEL_NAME to serviceUuid,
            COMPAT_CHANNEL_NAME to COMPAT_SERVICE_UUID
        )

        attempts.forEachIndexed { index, (channelName, uuid) ->
            if (index > 0) {
                emitStatus(
                    BluetoothConnectionState.CONNECTING,
                    "安全通道失败，正在尝试$channelName",
                    connections.size
                )
            }
            val socket = if (channelName == SECURE_CHANNEL_NAME) {
                device.createRfcommSocketToServiceRecord(uuid)
            } else {
                device.createInsecureRfcommSocketToServiceRecord(uuid)
            }
            try {
                socket.connect()
                return socket
            } catch (error: IOException) {
                runCatching { socket.close() }
                errors.add("$channelName：${error.message.orEmpty()}")
            } catch (error: SecurityException) {
                runCatching { socket.close() }
                throw error
            }
        }

        throw IOException(
            "蓝牙连接失败。请确认两台手机已在系统蓝牙配对、房主停留在等待接入页面，并在客户端“已配对”里选择房主手机。失败详情：${errors.joinToString("；")}"
        )
    }

    /** 将新 socket 纳入连接池，并启动该连接的读循环。 */
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

    /** 持续读取远端按行发送的协议消息。 */
    private fun readLoop(connectionId: String, connection: Connection) {
        try {
            connection.reader.use { reader ->
                while (!closed) {
                    val line = reader.readLine() ?: break
                    val message = BluetoothMessageCodec.decode(line)
                    if (message == null) {
                        emitMessage(BluetoothMessage.Error("无法解析蓝牙消息：$line"))
                    } else {
                        // 先记录消息来源与玩家 id 的关系，再把消息交给业务层处理。
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

    /** 根据用户输入的 MAC 地址或已配对设备名找到要连接的主机。 */
    @SuppressLint("MissingPermission")
    private fun findRemoteDevice(roomId: String): BluetoothDevice {
        val bluetoothAdapter = adapter ?: throw IllegalArgumentException("设备不支持蓝牙")
        val requestedDevice = roomId.trim()
        val addressInText = MAC_ADDRESS_IN_TEXT.find(requestedDevice)?.value?.uppercase()
        if (addressInText != null && BluetoothAdapter.checkBluetoothAddress(addressInText)) {
            return bluetoothAdapter.getRemoteDevice(addressInText)
        }

        return bluetoothAdapter.bondedDevices.firstOrNull { device ->
            device.address.equals(requestedDevice, ignoreCase = true) ||
                device.name.orEmpty().equals(requestedDevice, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "没有找到已配对主机设备：$requestedDevice。请先在系统蓝牙完成配对，再点“已配对”选择主机手机；不要填写房主随机房间号。"
        )
    }

    /** 检查设备是否支持蓝牙且蓝牙已打开。 */
    private fun ensureBluetoothReady(): Boolean {
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) {
            emitStatus(BluetoothConnectionState.ERROR, "设备不支持蓝牙")
            return false
        }
        val enabled = runCatching { bluetoothAdapter.isEnabled }.getOrElse { error ->
            emitStatus(BluetoothConnectionState.ERROR, "缺少蓝牙连接权限：${error.message.orEmpty()}")
            return false
        }
        if (!enabled) {
            emitStatus(BluetoothConnectionState.ERROR, "请先打开系统蓝牙")
            return false
        }
        return true
    }

    /** 断开后再次创建/加入房间时，需要重建已经 shutdown 的线程池。 */
    private fun ensureExecutor() {
        if (executor.isShutdown || executor.isTerminated) {
            executor = Executors.newCachedThreadPool()
        }
    }

    /** 关闭单条连接并按需向上层报告玩家离线。 */
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

    /** 从同一连接绑定过的别名中优先找正式座位 id，便于离线提示准确。 */
    private fun offlinePlayerIdFor(connectionId: String): String {
        val aliases = playerConnectionIds.entries
            .filter { it.value == connectionId }
            .map { it.key }

        return aliases.firstOrNull { FORMAL_PLAYER_ID.matches(it) }
            ?: aliases.firstOrNull { !it.startsWith("guest-") }
            ?: aliases.firstOrNull()
            ?: connectionId
    }

    /** 关闭所有连接和监听 socket。 */
    private fun closeSockets() {
        closeServerSockets()
        connections.keys.toList().forEach { closeConnection(it, notifyOffline = false) }
        playerConnectionIds.clear()
    }

    /** 只关闭房主监听 socket，不影响已经建立的客户端连接。 */
    private fun closeServerSockets() {
        serverSockets.values.forEach { serverSocket ->
            runCatching { serverSocket.close() }
        }
        serverSockets.clear()
    }

    /** 将消息回调投递到主线程。 */
    private fun emitMessage(message: BluetoothMessage) {
        val callback = messageCallback ?: return
        mainHandler.post { callback(message) }
    }

    /** 将连接状态回调投递到主线程。 */
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

    /** Android 蓝牙服务名中带上房间号，便于调试和区分当前房间。 */
    private fun serviceName(roomId: String): String = "ChuDaDi-$roomId"

    /** 从带 playerId 的业务消息中学习连接与玩家的映射关系。 */
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

    /** 单条 RFCOMM socket 的读写封装。 */
    private class Connection(socket: BluetoothSocket) {
        val reader = BufferedReader(
            InputStreamReader(socket.inputStream, StandardCharsets.UTF_8)
        )
        private val writer = BufferedWriter(
            OutputStreamWriter(socket.outputStream, StandardCharsets.UTF_8)
        )
        private val socketRef = socket

        /** 多线程发送时串行化 writer，避免两条协议消息交错写入。 */
        @Synchronized
        fun write(line: String) {
            writer.write(line)
            writer.newLine()
            writer.flush()
        }

        /** 关闭读写流和底层 socket。 */
        fun close() {
            runCatching { writer.close() }
            runCatching { reader.close() }
            runCatching { socketRef.close() }
        }
    }

    companion object {
        /** 主服务 UUID，房主和客户端必须保持一致才能建连。 */
        val CHUDADI_SERVICE_UUID: UUID = UUID.fromString("a9695c24-48b7-4c71-a4fb-f1056c97f751")
        /** 兼容通道使用独立 UUID，避免与安全通道冲突。 */
        private val COMPAT_SERVICE_UUID: UUID = UUID.fromString("c5c860d7-26b1-45f5-a4c3-4d3fbad6604b")
        /** 正式游戏座位 id，只允许 p1 到 p4。 */
        private val FORMAL_PLAYER_ID = Regex("p[1-4]")
        /** 从输入框文本中容错提取 MAC 地址。 */
        private val MAC_ADDRESS_IN_TEXT = Regex("(?i)([0-9A-F]{2}:){5}[0-9A-F]{2}")
        private const val SECURE_CHANNEL_NAME = "安全通道"
        private const val COMPAT_CHANNEL_NAME = "兼容通道"
        /** 房主之外最多三个客户端座位。 */
        private const val DEFAULT_MAX_CLIENT_COUNT = 3
    }
}
