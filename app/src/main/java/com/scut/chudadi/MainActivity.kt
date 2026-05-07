package com.scut.chudadi

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.scut.chudadi.ai.ConservativeStrategy
import com.scut.chudadi.ai.GreedyStrategy
import com.scut.chudadi.ai.PlayCandidateFinder
import com.scut.chudadi.ai.PlayStrategy
import com.scut.chudadi.controller.GameController
import com.scut.chudadi.model.Card
import com.scut.chudadi.model.GameConfig
import com.scut.chudadi.model.HandType
import com.scut.chudadi.model.PlayerState
import com.scut.chudadi.model.Rank
import com.scut.chudadi.model.RuleSetType
import com.scut.chudadi.model.ScoringMode
import com.scut.chudadi.model.Suit
import com.scut.chudadi.network.BluetoothConnectionState
import com.scut.chudadi.network.BluetoothGameSyncManager
import com.scut.chudadi.network.BluetoothMessage
import com.scut.chudadi.network.BluetoothPeer
import com.scut.chudadi.network.BluetoothPermissionHelper
import com.scut.chudadi.network.BluetoothStatus
import com.scut.chudadi.network.CardWireCodec
import com.scut.chudadi.rule.HandEvaluator
import com.scut.chudadi.rule.RuleEngine

class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var tvLastPlay: TextView
    private lateinit var tvSelection: TextView
    private lateinit var tvMessage: TextView
    private lateinit var tvLog: TextView
    private lateinit var playerBoard: LinearLayout
    private lateinit var cardContainer: LinearLayout
    private lateinit var btnPlay: Button
    private lateinit var btnPass: Button
    private lateinit var btnHint: Button
    private lateinit var btnNewGame: Button
    private lateinit var btnResetMatch: Button
    private lateinit var etBluetoothRoom: EditText
    private lateinit var tvBluetoothStatus: TextView
    private lateinit var btnBluetoothPermission: Button
    private lateinit var btnBluetoothDevices: Button
    private lateinit var btnBluetoothReady: Button
    private lateinit var btnBluetoothReconnect: Button
    private lateinit var btnBluetoothHost: Button
    private lateinit var btnBluetoothJoin: Button
    private lateinit var btnBluetoothDisconnect: Button
    private lateinit var pairedDeviceBoard: LinearLayout
    private lateinit var roomStateBoard: LinearLayout
    private lateinit var setupPage: View
    private lateinit var roomPage: FrameLayout
    private lateinit var tvTableStatus: TextView
    private lateinit var tvTableLastPlay: TextView
    private lateinit var tvTableMessage: TextView
    private lateinit var tableTopSeats: LinearLayout
    private lateinit var tableLeftSeats: LinearLayout
    private lateinit var tableRightSeats: LinearLayout
    private lateinit var tableLastPlayCards: LinearLayout
    private lateinit var tableLocalHud: LinearLayout
    private lateinit var tableCardContainer: LinearLayout
    private lateinit var btnTablePlay: Button
    private lateinit var btnTablePass: Button
    private lateinit var btnTableHint: Button
    private lateinit var btnTableNewGame: Button
    private lateinit var btnTableLeaveRoom: Button
    private lateinit var rbSouth: RadioButton
    private lateinit var rbNorth: RadioButton

    private lateinit var controller: GameController
    private val matchScores = mutableMapOf<String, Int>()
    private val selectedCards = linkedSetOf<Card>()
    private val logLines = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var soundPool: SoundPool
    private var selectSoundId = 0
    private var playSoundId = 0
    private var passSoundId = 0
    private var errorSoundId = 0
    private var syncManager: BluetoothGameSyncManager? = null
    private val roomPlayers = linkedSetOf(HUMAN_ID)
    private val readyPlayers = linkedSetOf(HUMAN_ID)
    private val bluetoothHumanSeats = linkedSetOf<String>()
    private val lastHeartbeatByPlayer = mutableMapOf<String, Long>()
    private val clientSeatByRequestId = mutableMapOf<String, String>()
    private var bluetoothRole = BluetoothRole.LOCAL
    private var localPlayerId = HUMAN_ID
    private var networkPlayerId = HUMAN_ID
    private var syncManagerOwnerId: String? = null
    private var currentRoomId = ""
    private var lastWinnerId: String? = null
    private var waitingForHost = false
    private var roomGameStarted = false
    private var lastHeartbeatAt = 0L
    private var heartbeatTimeoutReported = false
    private var roundNumber = 0
    private var roundOver = false

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (bluetoothRole != BluetoothRole.LOCAL) {
                val now = System.currentTimeMillis()
                syncManager?.sendMessage(BluetoothMessage.Heartbeat(now, localPlayerId))
                checkHeartbeatTimeout(now)
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            tvBluetoothStatus.text = if (granted) {
                "蓝牙权限已授权，可以创建或加入房间。"
            } else {
                "蓝牙权限未完全授权，无法联机。"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvLastPlay = findViewById(R.id.tvLastPlay)
        tvSelection = findViewById(R.id.tvSelection)
        tvMessage = findViewById(R.id.tvMessage)
        tvLog = findViewById(R.id.tvLog)
        playerBoard = findViewById(R.id.playerBoard)
        cardContainer = findViewById(R.id.cardContainer)
        btnPlay = findViewById(R.id.btnPlay)
        btnPass = findViewById(R.id.btnPass)
        btnHint = findViewById(R.id.btnHint)
        btnNewGame = findViewById(R.id.btnNewGame)
        btnResetMatch = findViewById(R.id.btnResetMatch)
        etBluetoothRoom = findViewById(R.id.etBluetoothRoom)
        tvBluetoothStatus = findViewById(R.id.tvBluetoothStatus)
        btnBluetoothPermission = findViewById(R.id.btnBluetoothPermission)
        btnBluetoothDevices = findViewById(R.id.btnBluetoothDevices)
        btnBluetoothReady = findViewById(R.id.btnBluetoothReady)
        btnBluetoothReconnect = findViewById(R.id.btnBluetoothReconnect)
        btnBluetoothHost = findViewById(R.id.btnBluetoothHost)
        btnBluetoothJoin = findViewById(R.id.btnBluetoothJoin)
        btnBluetoothDisconnect = findViewById(R.id.btnBluetoothDisconnect)
        pairedDeviceBoard = findViewById(R.id.pairedDeviceBoard)
        roomStateBoard = findViewById(R.id.roomStateBoard)
        setupPage = findViewById(R.id.setupPage)
        roomPage = findViewById(R.id.roomPage)
        tvTableStatus = findViewById(R.id.tvTableStatus)
        tvTableLastPlay = findViewById(R.id.tvTableLastPlay)
        tvTableMessage = findViewById(R.id.tvTableMessage)
        tableTopSeats = findViewById(R.id.tableTopSeats)
        tableLeftSeats = findViewById(R.id.tableLeftSeats)
        tableRightSeats = findViewById(R.id.tableRightSeats)
        tableLastPlayCards = findViewById(R.id.tableLastPlayCards)
        tableLocalHud = findViewById(R.id.tableLocalHud)
        tableCardContainer = findViewById(R.id.tableCardContainer)
        btnTablePlay = findViewById(R.id.btnTablePlay)
        btnTablePass = findViewById(R.id.btnTablePass)
        btnTableHint = findViewById(R.id.btnTableHint)
        btnTableNewGame = findViewById(R.id.btnTableNewGame)
        btnTableLeaveRoom = findViewById(R.id.btnTableLeaveRoom)
        rbSouth = findViewById(R.id.rbSouth)
        rbNorth = findViewById(R.id.rbNorth)

        initAudioFeedback()

        btnNewGame.setOnClickListener { startNewGame() }
        btnResetMatch.setOnClickListener { resetMatch() }
        btnPlay.setOnClickListener { playSelectedCards() }
        btnPass.setOnClickListener { passTurn() }
        btnHint.setOnClickListener { selectHintCards() }
        btnBluetoothPermission.setOnClickListener { requestBluetoothPermissions() }
        btnBluetoothDevices.setOnClickListener { showBondedBluetoothDevices() }
        btnBluetoothReady.setOnClickListener { markBluetoothReady() }
        btnBluetoothReconnect.setOnClickListener { requestBluetoothReconnect() }
        btnBluetoothHost.setOnClickListener { showRoomSeatDialog() }
        btnBluetoothJoin.setOnClickListener { joinBluetoothRoom() }
        btnBluetoothDisconnect.setOnClickListener { disconnectBluetoothRoom() }
        btnTablePlay.setOnClickListener { playSelectedCards() }
        btnTablePass.setOnClickListener { passTurn() }
        btnTableHint.setOnClickListener { selectHintCards() }
        btnTableNewGame.setOnClickListener { startNewGame() }
        btnTableLeaveRoom.setOnClickListener { leaveRoomPage() }

        startNewGame()
    }

    override fun onDestroy() {
        syncManager?.disconnect()
        if (::soundPool.isInitialized) {
            soundPool.release()
        }
        super.onDestroy()
    }

    private fun initAudioFeedback() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attributes)
            .build()
        selectSoundId = soundPool.load(this, R.raw.ui_select, 1)
        playSoundId = soundPool.load(this, R.raw.ui_play, 1)
        passSoundId = soundPool.load(this, R.raw.ui_pass, 1)
        errorSoundId = soundPool.load(this, R.raw.ui_error, 1)
    }

    private fun playUiSound(soundId: Int, volume: Float = 0.55f) {
        if (!::soundPool.isInitialized || soundId == 0) return
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    private fun requestBluetoothPermissions() {
        bluetoothPermissionLauncher.launch(BluetoothPermissionHelper.requiredPermissions())
    }

    private fun showRoomSeatDialog() {
        val labels = arrayOf("玩家 2 使用蓝牙真人", "玩家 3 使用蓝牙真人", "玩家 4 使用蓝牙真人")
        val checked = booleanArrayOf(false, false, false)
        AlertDialog.Builder(this)
            .setTitle("创建房间")
            .setMessage("勾选真人座位；未勾选的座位由 AI 托管。全选 AI 会直接开始对局。")
            .setMultiChoiceItems(labels, checked) { _, index, isChecked ->
                checked[index] = isChecked
            }
            .setNegativeButton("取消", null)
            .setPositiveButton("创建") { _, _ ->
                val humanSeats = PLAYER_IDS.drop(1).filterIndexed { index, _ -> checked[index] }.toSet()
                if (humanSeats.isEmpty()) {
                    createLocalAiRoom()
                } else {
                    hostBluetoothRoom(humanSeats)
                }
            }
            .show()
    }

    private fun createLocalAiRoom() {
        syncManager?.disconnect()
        bluetoothRole = BluetoothRole.LOCAL
        localPlayerId = HUMAN_ID
        networkPlayerId = HUMAN_ID
        syncManagerOwnerId = null
        currentRoomId = roomIdFromInput(defaultValue = randomRoomId())
        waitingForHost = false
        roomPlayers.clear()
        roomPlayers.add(localPlayerId)
        readyPlayers.clear()
        readyPlayers.add(localPlayerId)
        bluetoothHumanSeats.clear()
        clientSeatByRequestId.clear()
        enterRoomPage()
        addLog("房间 $currentRoomId：3 个 AI 托管，自动开局")
        startNewGame()
    }

    private fun hostBluetoothRoom(humanSeats: Set<String> = PLAYER_IDS.drop(1).toSet()) {
        if (!BluetoothPermissionHelper.hasRequiredPermissions(this)) {
            requestBluetoothPermissions()
            return
        }

        bluetoothRole = BluetoothRole.HOST
        localPlayerId = HUMAN_ID
        networkPlayerId = HUMAN_ID
        currentRoomId = roomIdFromInput(defaultValue = randomRoomId())
        clientSeatByRequestId.clear()
        bluetoothHumanSeats.clear()
        bluetoothHumanSeats.addAll(humanSeats)
        lastHeartbeatByPlayer.clear()
        roomPlayers.clear()
        roomPlayers.add(localPlayerId)
        readyPlayers.clear()
        readyPlayers.add(localPlayerId)
        ensureSyncManager(networkPlayerId).hostRoom(currentRoomId)
        startHeartbeatLoop()
        roomGameStarted = false
        enterRoomPage()
        addLog("蓝牙：创建房间 $currentRoomId，等待 ${humanSeats.size} 位真人加入")
        renderRoomState()
        renderTablePage()
    }

    private fun joinBluetoothRoom() {
        if (!BluetoothPermissionHelper.hasRequiredPermissions(this)) {
            requestBluetoothPermissions()
            return
        }

        currentRoomId = roomIdFromInput(defaultValue = "")
        if (currentRoomId.isEmpty()) {
            tvBluetoothStatus.text = "请输入主机蓝牙 MAC 地址或已配对设备名。"
            return
        }

        bluetoothRole = BluetoothRole.CLIENT
        localPlayerId = HUMAN_ID
        networkPlayerId = "guest-${System.currentTimeMillis()}"
        bluetoothHumanSeats.clear()
        lastHeartbeatByPlayer.clear()
        roomPlayers.clear()
        roomPlayers.add(localPlayerId)
        readyPlayers.clear()
        roomGameStarted = false
        ensureSyncManager(networkPlayerId).joinRoom(currentRoomId)
        startHeartbeatLoop()
        enterRoomPage()
        addLog("蓝牙：尝试加入 $currentRoomId")
    }

    private fun disconnectBluetoothRoom() {
        syncManager?.disconnect()
        bluetoothRole = BluetoothRole.LOCAL
        localPlayerId = HUMAN_ID
        networkPlayerId = HUMAN_ID
        syncManagerOwnerId = null
        currentRoomId = ""
        waitingForHost = false
        lastHeartbeatAt = 0L
        heartbeatTimeoutReported = false
        lastHeartbeatByPlayer.clear()
        bluetoothHumanSeats.clear()
        clientSeatByRequestId.clear()
        roomPlayers.clear()
        roomPlayers.add(localPlayerId)
        readyPlayers.clear()
        readyPlayers.add(localPlayerId)
        tvBluetoothStatus.text = "蓝牙未连接"
        roomGameStarted = false
        renderRoomState()
    }

    private fun enterRoomPage() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setupPage.visibility = View.GONE
        roomPage.visibility = View.VISIBLE
        renderTablePage()
    }

    private fun leaveRoomPage() {
        disconnectBluetoothRoom()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        roomPage.visibility = View.GONE
        setupPage.visibility = View.VISIBLE
        render()
    }

    private fun startHeartbeatLoop() {
        handler.removeCallbacks(heartbeatRunnable)
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    private fun markBluetoothReady() {
        if (bluetoothRole == BluetoothRole.LOCAL) {
            tvBluetoothStatus.text = "本地模式不需要蓝牙准备。"
            return
        }

        readyPlayers.add(localPlayerId)
        syncManager?.sendMessage(BluetoothMessage.Ready(localPlayerId))
        if (bluetoothRole == BluetoothRole.HOST) {
            broadcastRoomState()
        }
        tvBluetoothStatus.text = "已准备：${readyPlayers.joinToString("，")}"
        addLog("蓝牙：${localPlayerId} 已准备")
        renderLog()
        renderRoomState()
    }

    private fun requestBluetoothReconnect() {
        if (bluetoothRole == BluetoothRole.LOCAL) {
            tvBluetoothStatus.text = "本地模式不需要重连。"
            return
        }

        waitingForHost = false
        heartbeatTimeoutReported = false
        lastHeartbeatAt = System.currentTimeMillis()
        if (bluetoothRole == BluetoothRole.CLIENT) {
            val roomId = currentRoomId.ifEmpty { roomIdFromInput(defaultValue = "") }
            if (roomId.isNotEmpty()) {
                currentRoomId = roomId
                if (localPlayerId == HUMAN_ID) {
                    ensureSyncManager(networkPlayerId).joinRoom(roomId)
                } else {
                    ensureSyncManager(localPlayerId).reconnectRoom(roomId, localPlayerId)
                }
            } else {
                syncManager?.sendMessage(BluetoothMessage.Reconnect(localPlayerId))
            }
        } else {
            broadcastRoomState()
            sendBluetoothSnapshot()
        }
        startHeartbeatLoop()
        addLog("蓝牙：${localPlayerId} 请求重连")
        renderLog()
    }

    private fun showBondedBluetoothDevices() {
        if (!BluetoothPermissionHelper.hasRequiredPermissions(this)) {
            requestBluetoothPermissions()
            return
        }

        val peers = ensureSyncManager(networkPlayerId).bondedPeers()
        renderBondedPeers(peers)
    }

    private fun ensureSyncManager(ownerId: String = networkPlayerId): BluetoothGameSyncManager {
        val existing = syncManager
        if (existing != null && syncManagerOwnerId == ownerId) return existing

        existing?.disconnect()

        return BluetoothGameSyncManager(this, ownerId).also { manager ->
            manager.onStatus(::onBluetoothStatus)
            manager.onMessage(::onBluetoothMessage)
            syncManager = manager
            syncManagerOwnerId = ownerId
        }
    }

    private fun onBluetoothStatus(status: BluetoothStatus) {
        tvBluetoothStatus.text = buildString {
            append(statusLabel(status.state))
            if (status.detail.isNotEmpty()) append("：${status.detail}")
            append("    连接数：${status.connectedCount}")
        }
        if (status.detail.isNotEmpty()) {
            addLog("蓝牙状态：${status.detail}")
            renderLog()
        }
        if (status.state == BluetoothConnectionState.CONNECTED) {
            lastHeartbeatAt = System.currentTimeMillis()
            heartbeatTimeoutReported = false
        }
        renderRoomState()
    }

    private fun renderBondedPeers(peers: List<BluetoothPeer>) {
        pairedDeviceBoard.removeAllViews()
        if (peers.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "没有已配对设备。请先在系统蓝牙设置中配对主机手机。"
                setTextColor(Color.parseColor("#FFF4D6"))
                background = roundedBackground("#B711251D", "#448AC09E", 8)
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            pairedDeviceBoard.addView(emptyView)
            return
        }

        peers.forEach { peer ->
            val button = Button(this).apply {
                text = peer.displayLabel
                setAllCaps(false)
                minHeight = dp(44)
                setTextColor(Color.parseColor("#FFF4D6"))
                background = resources.getDrawable(R.drawable.button_secondary, theme)
                compoundDrawablePadding = dp(6)
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_bluetooth_24, 0, 0, 0)
                setOnClickListener {
                    playUiSound(selectSoundId)
                    etBluetoothRoom.setText(peer.address)
                    tvBluetoothStatus.text = "已选择：${peer.displayLabel}"
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            pairedDeviceBoard.addView(button, params)
        }
    }

    private fun onBluetoothMessage(message: BluetoothMessage) {
        val now = System.currentTimeMillis()
        if (bluetoothRole == BluetoothRole.CLIENT) {
            lastHeartbeatAt = now
            heartbeatTimeoutReported = false
        }

        when (message) {
            is BluetoothMessage.JoinRoom -> {
                if (bluetoothRole == BluetoothRole.HOST) {
                    val assignedSeat = assignSeat(message.playerId)
                    if (assignedSeat == null) {
                        syncManager?.sendMessage(BluetoothMessage.Error("没有空余蓝牙真人座位，无法加入"))
                        addLog("蓝牙：拒绝 ${message.playerName}，没有空余蓝牙真人座位")
                        renderLog()
                        return
                    }

                    ensureSyncManager().bindPlayerAlias(message.playerId, assignedSeat)
                    noteRemoteActivity(assignedSeat, now)
                    roomPlayers.add(assignedSeat)
                    readyPlayers.remove(assignedSeat)
                    addLog("蓝牙：${message.playerName} 加入房间，座位 $assignedSeat")
                    syncManager?.sendMessage(
                        BluetoothMessage.SeatAssigned(
                            requestPlayerId = message.playerId,
                            assignedPlayerId = assignedSeat,
                            players = roomPlayers.toList()
                        )
                    )
                    broadcastRoomState()
                    startGameWhenRoomReady()
                } else {
                    addLog("蓝牙：${message.playerName} 加入房间")
                }
            }
            is BluetoothMessage.SeatAssigned -> {
                if (message.requestPlayerId == networkPlayerId) {
                    localPlayerId = message.assignedPlayerId
                    networkPlayerId = message.assignedPlayerId
                    syncManagerOwnerId = message.assignedPlayerId
                    roomPlayers.clear()
                    roomPlayers.addAll(message.players)
                    readyPlayers.clear()
                    lastHeartbeatByPlayer.clear()
                    addLog("蓝牙：你被分配到座位 ${message.assignedPlayerId}")
                } else {
                    roomPlayers.clear()
                    roomPlayers.addAll(message.players)
                    addLog("蓝牙：${message.requestPlayerId} 被分配到 ${message.assignedPlayerId}")
                }
            }
            is BluetoothMessage.Ready -> {
                if (bluetoothRole == BluetoothRole.HOST) {
                    noteRemoteActivity(message.playerId, now)
                }
                readyPlayers.add(message.playerId)
                addLog("蓝牙：${message.playerId} 已准备")
                if (bluetoothRole == BluetoothRole.HOST) {
                    broadcastRoomState()
                    startGameWhenRoomReady()
                }
            }
            is BluetoothMessage.RoomState -> {
                roomPlayers.clear()
                roomPlayers.addAll(message.players)
                readyPlayers.clear()
                readyPlayers.addAll(message.readyPlayers)
                bluetoothHumanSeats.clear()
                bluetoothHumanSeats.addAll(message.bluetoothPlayers.filter { it in PLAYER_IDS && it != HUMAN_ID })
                addLog("蓝牙房间玩家：${message.players.joinToString("，")}")
            }
            is BluetoothMessage.StartGame -> {
                addLog("蓝牙：收到开局种子 ${message.seed}")
                if (bluetoothRole != BluetoothRole.HOST) {
                    startNewGame(seed = message.seed, broadcastStart = false)
                }
            }
            is BluetoothMessage.PlayCards -> {
                if (bluetoothRole == BluetoothRole.HOST) {
                    noteRemoteActivity(message.playerId, now)
                }
                handleRemotePlayCards(message)
            }
            is BluetoothMessage.Pass -> {
                if (bluetoothRole == BluetoothRole.HOST) {
                    noteRemoteActivity(message.playerId, now)
                }
                handleRemotePass(message)
            }
            is BluetoothMessage.PrivateHand -> {
                applyPrivateHand(message)
            }
            is BluetoothMessage.GameStateSnapshot -> {
                applyBluetoothSnapshot(message)
            }
            is BluetoothMessage.RoundResult -> {
                addLog("蓝牙结算：${message.scoreMap.entries.joinToString("，") { "${it.key} ${it.value}" }}")
            }
            is BluetoothMessage.PlayerOffline -> {
                markPlayerOffline(
                    playerId = message.playerId,
                    reason = "连接断开",
                    broadcast = bluetoothRole == BluetoothRole.HOST
                )
            }
            is BluetoothMessage.Reconnect -> {
                noteRemoteActivity(message.playerId, now)
                addLog("蓝牙：${message.playerId} 请求重连")
                if (
                    bluetoothRole == BluetoothRole.HOST &&
                    message.playerId in PLAYER_IDS &&
                    message.playerId in bluetoothHumanSeats
                ) {
                    roomPlayers.add(message.playerId)
                    readyPlayers.add(message.playerId)
                    ensureSyncManager().bindPlayerAlias(message.playerId, message.playerId)
                    broadcastRoomState()
                    if (::controller.isInitialized) {
                        syncManager?.sendMessage(BluetoothMessage.StartGame(controller.state.roundSeed))
                        sendBluetoothSnapshot()
                    }
                } else if (bluetoothRole == BluetoothRole.HOST) {
                    syncManager?.sendMessage(BluetoothMessage.Error("该座位当前不是蓝牙真人座位"))
                }
            }
            is BluetoothMessage.Heartbeat -> {
                val heartbeatPlayerId = message.playerId
                if (bluetoothRole == BluetoothRole.HOST && heartbeatPlayerId in PLAYER_IDS) {
                    noteRemoteActivity(heartbeatPlayerId, now)
                } else {
                    lastHeartbeatAt = now
                }
                heartbeatTimeoutReported = false
                tvBluetoothStatus.text = if (heartbeatPlayerId.isBlank()) {
                    "蓝牙心跳：${message.timestamp}"
                } else {
                    "蓝牙心跳：$heartbeatPlayerId"
                }
            }
            is BluetoothMessage.Error -> {
                waitingForHost = false
                addLog("蓝牙错误：${message.reason}")
            }
        }
        renderLog()
        renderRoomState()
        renderTablePage()
    }

    private fun startGameWhenRoomReady() {
        if (bluetoothRole == BluetoothRole.HOST && !roomGameStarted && allJoinedPlayersReady()) {
            addLog("蓝牙：真人座位已准备，自动开局")
            startNewGame()
        }
    }

    private fun handleRemotePlayCards(message: BluetoothMessage.PlayCards) {
        val cards = CardWireCodec.decodeList(message.cards)
        if (cards == null) {
            addLog("蓝牙：${message.playerId} 的出牌无法解析")
            if (bluetoothRole == BluetoothRole.HOST) {
                syncManager?.sendMessage(BluetoothMessage.Error("出牌格式错误：${message.playerId}"))
            }
            return
        }

        if (bluetoothRole == BluetoothRole.HOST && message.playerId != localPlayerId) {
            val play = HandEvaluator.evaluate(cards, controller.ruleProfile)
            if (play == null || !controller.playCards(message.playerId, cards)) {
                addLog("蓝牙：拒绝 ${message.playerId} 的非法出牌 ${message.cards.joinToString(" ")}")
                syncManager?.sendMessage(BluetoothMessage.Error("非法出牌：${message.playerId}"))
                sendBluetoothSnapshot()
                return
            }

            addLog("${playerName(message.playerId)} 出牌 ${typeName(play.type)}：${cardsLabel(cards)}")
            syncManager?.sendMessage(
                BluetoothMessage.PlayCards(message.playerId, CardWireCodec.encodeList(cards))
            )
            sendBluetoothSnapshot()
            afterAction()
            return
        }

        val label = if (message.playerId == localPlayerId) "主机确认你出牌" else "蓝牙：${message.playerId} 出牌"
        if (message.playerId == localPlayerId) waitingForHost = false
        addLog("$label ${cardsLabel(cards)}")
    }

    private fun handleRemotePass(message: BluetoothMessage.Pass) {
        if (bluetoothRole == BluetoothRole.HOST && message.playerId != localPlayerId) {
            if (!controller.pass(message.playerId)) {
                addLog("蓝牙：拒绝 ${message.playerId} 的非法过牌")
                syncManager?.sendMessage(BluetoothMessage.Error("非法过牌：${message.playerId}"))
                sendBluetoothSnapshot()
                return
            }

            addLog("${playerName(message.playerId)} 过牌")
            syncManager?.sendMessage(BluetoothMessage.Pass(message.playerId))
            sendBluetoothSnapshot()
            afterAction()
            return
        }

        val label = if (message.playerId == localPlayerId) "主机确认你过牌" else "蓝牙：${message.playerId} 过牌"
        if (message.playerId == localPlayerId) waitingForHost = false
        addLog(label)
    }

    private fun applyBluetoothSnapshot(message: BluetoothMessage.GameStateSnapshot) {
        if (!::controller.isInitialized || controller.state.roundSeed != message.seed) {
            startNewGame(seed = message.seed, broadcastStart = false)
        }

        controller.state.players.forEach { player ->
            message.scores[player.id]?.let { score ->
                player.score = score
                matchScores[player.id] = score
            }
        }

        val currentIndex = controller.state.players.indexOfFirst { it.id == message.currentPlayerId }
        if (currentIndex >= 0) controller.state.currentPlayerIndex = currentIndex

        controller.state.lastPlay = CardWireCodec.decodeList(message.lastPlayCards)
            ?.takeIf { it.isNotEmpty() }
            ?.let { HandEvaluator.evaluate(it, controller.ruleProfile) }
        controller.state.passCount = message.passCount
        controller.state.firstRound = message.firstRound
        controller.state.lastWinnerId = message.lastWinnerId
        controller.state.finishOrder.clear()
        controller.state.finishOrder.addAll(message.finishOrder)
        lastWinnerId = message.lastWinnerId
        roundOver = message.finishOrder.isNotEmpty()
        selectedCards.clear()
        waitingForHost = false

        addLog(
            "蓝牙快照：当前 ${message.currentPlayerId}，手牌数 ${
                message.handCounts.entries.joinToString("，") { "${it.key}:${it.value}" }
            }"
        )
        render()
    }

    private fun applyPrivateHand(message: BluetoothMessage.PrivateHand) {
        if (message.playerId != localPlayerId) return
        val cards = CardWireCodec.decodeList(message.cards)
        if (cards == null) {
            addLog("蓝牙：收到无法解析的私人手牌")
            return
        }

        humanPlayer().handCards.clear()
        humanPlayer().handCards.addAll(sortHand(cards))
        selectedCards.clear()
        addLog("蓝牙：已更新你的私人手牌，共 ${cards.size} 张")
        render()
    }

    private fun startNewGame(
        seed: Long = System.currentTimeMillis(),
        broadcastStart: Boolean = true
    ) {
        if (broadcastStart && bluetoothRole == BluetoothRole.HOST && !allJoinedPlayersReady()) {
            val waiting = waitingHumanSeats()
            tvBluetoothStatus.text = "还有玩家未准备：${waiting.joinToString("，")}"
            addLog("蓝牙：等待玩家准备 ${waiting.joinToString("，")}")
            renderLog()
            return
        }

        handler.removeCallbacksAndMessages(null)
        selectedCards.clear()
        roundOver = false
        roomGameStarted = true
        roundNumber += 1

        val ruleSetType = if (rbNorth.isChecked) RuleSetType.NORTH else RuleSetType.SOUTH
        val players = createPlayers()
        val config = GameConfig(scoringMode = ScoringMode.SCORE, ruleSetType = ruleSetType)
        controller = GameController(config, players)
        controller.state.lastWinnerId = lastWinnerId
        controller.startGame(seed)

        addLog("第 ${roundNumber} 局：${controller.ruleProfile.displayName}开局")
        addLog("先手：${currentPlayer().name}")
        if (broadcastStart && bluetoothRole == BluetoothRole.HOST) {
            syncManager?.sendMessage(BluetoothMessage.StartGame(seed))
            sendBluetoothSnapshot()
        }
        if (bluetoothRole != BluetoothRole.LOCAL) {
            startHeartbeatLoop()
        }
        tvMessage.text = if (currentPlayer().id == localPlayerId) {
            "请选择手牌出牌。"
        } else {
            "等待其他玩家。"
        }
        render()
        runAiTurns()
    }

    private fun resetMatch() {
        handler.removeCallbacksAndMessages(null)
        selectedCards.clear()
        logLines.clear()
        matchScores.clear()
        lastWinnerId = null
        roundNumber = 0
        roundOver = false
        roomGameStarted = false
        if (bluetoothRole != BluetoothRole.LOCAL) {
            startHeartbeatLoop()
        }
        startNewGame()
    }

    private fun playSelectedCards() {
        if (roundOver || currentPlayer().id != localPlayerId) return
        if (selectedCards.isEmpty()) {
            playUiSound(errorSoundId)
            tvMessage.text = "先点选你要出的牌。"
            return
        }

        val cards = selectedCards.toList().sorted()
        val play = HandEvaluator.evaluate(cards, controller.ruleProfile)
        if (play == null) {
            playUiSound(errorSoundId)
            tvMessage.text = "这组牌不是合法牌型。"
            return
        }

        if (!RuleEngine.canPlay(controller.state, humanPlayer().handCards, cards, controller.ruleProfile)) {
            playUiSound(errorSoundId)
            tvMessage.text = invalidPlayMessage(cards)
            return
        }

        if (bluetoothRole == BluetoothRole.CLIENT) {
            syncManager?.sendMessage(
                BluetoothMessage.PlayCards(localPlayerId, CardWireCodec.encodeList(cards))
            )
            waitingForHost = true
            playUiSound(playSoundId)
            tvMessage.text = "已发送出牌请求，等待主机确认。"
            selectedCards.clear()
            render()
            return
        }

        if (!controller.playCards(localPlayerId, cards)) {
            playUiSound(errorSoundId)
            tvMessage.text = invalidPlayMessage(cards)
            return
        }

        playUiSound(playSoundId)
        addLog("你 出牌 ${typeName(play.type)}：${cardsLabel(cards)}")
        syncManager?.sendMessage(BluetoothMessage.PlayCards(localPlayerId, CardWireCodec.encodeList(cards)))
        sendBluetoothSnapshot()
        selectedCards.clear()
        afterAction()
    }

    private fun passTurn() {
        if (roundOver || currentPlayer().id != localPlayerId) return
        if (!RuleEngine.canPass(controller.state)) {
            playUiSound(errorSoundId)
            tvMessage.text = "当前不能过牌，桌面没有上一手时必须出牌。"
            return
        }

        if (bluetoothRole == BluetoothRole.CLIENT) {
            syncManager?.sendMessage(BluetoothMessage.Pass(localPlayerId))
            waitingForHost = true
            playUiSound(passSoundId)
            tvMessage.text = "已发送过牌请求，等待主机确认。"
            selectedCards.clear()
            render()
            return
        }

        if (!controller.pass(localPlayerId)) {
            playUiSound(errorSoundId)
            tvMessage.text = "当前不能过牌，桌面没有上一手时必须出牌。"
            return
        }

        playUiSound(passSoundId)
        addLog("你 过牌")
        syncManager?.sendMessage(BluetoothMessage.Pass(localPlayerId))
        sendBluetoothSnapshot()
        selectedCards.clear()
        afterAction()
    }

    private fun selectHintCards() {
        if (roundOver || currentPlayer().id != localPlayerId) return

        val human = humanPlayer()
        val candidate = PlayCandidateFinder
            .findValidCandidates(controller.state, human.handCards, controller.ruleProfile)
            .firstOrNull()

        selectedCards.clear()
        if (candidate == null) {
            playUiSound(errorSoundId)
            tvMessage.text = "没有能压过上一手的牌，可以过牌。"
        } else {
            playUiSound(selectSoundId)
            selectedCards.addAll(candidate)
            tvMessage.text = "已选中建议出牌：${cardsLabel(candidate)}"
        }
        render()
    }

    private fun afterAction() {
        if (controller.state.finishOrder.isNotEmpty()) {
            finishRound()
            return
        }

        tvMessage.text = if (currentPlayer().id == localPlayerId) "轮到你了。" else "等待其他玩家..."
        render()
        runAiTurns()
    }

    private fun runAiTurns() {
        if (roundOver || currentPlayer().id == localPlayerId || shouldWaitForRemotePlayer()) {
            render()
            return
        }

        setActionButtonsEnabled(false)
        handler.postDelayed({
            if (roundOver || currentPlayer().id == localPlayerId || shouldWaitForRemotePlayer()) {
                render()
                return@postDelayed
            }

            val player = currentPlayer()
            val cards = strategyFor(player.id).chooseCards(
                controller.state,
                player.handCards,
                controller.ruleProfile
            )

            if (cards == null) {
                controller.pass(player.id)
                addLog("${player.name} 过牌")
                if (bluetoothRole == BluetoothRole.HOST) {
                    syncManager?.sendMessage(BluetoothMessage.Pass(player.id))
                    sendBluetoothSnapshot()
                }
            } else {
                val play = HandEvaluator.evaluate(cards, controller.ruleProfile)
                controller.playCards(player.id, cards)
                addLog("${player.name} 出牌 ${typeName(play?.type)}：${cardsLabel(cards)}")
                if (bluetoothRole == BluetoothRole.HOST) {
                    syncManager?.sendMessage(
                        BluetoothMessage.PlayCards(player.id, CardWireCodec.encodeList(cards))
                    )
                    sendBluetoothSnapshot()
                }
            }

            selectedCards.clear()
            afterAction()
        }, AI_TURN_DELAY_MS)
    }

    private fun finishRound() {
        roundOver = true
        selectedCards.clear()

        val scoreMap = controller.settleRound()
        scoreMap.forEach { (playerId, delta) ->
            matchScores[playerId] = (matchScores[playerId] ?: 0) + delta
        }
        controller.state.players.forEach { player ->
            player.score = matchScores[player.id] ?: 0
        }

        val winner = controller.state.players.firstOrNull {
            it.id == controller.state.finishOrder.firstOrNull()
        }
        lastWinnerId = winner?.id
        addLog("本局结束，赢家：${winner?.name ?: "未知"}")
        addLog("计分：${scoreMap.entries.joinToString("，") { "${playerName(it.key)} ${it.value}" }}")
        syncManager?.sendMessage(BluetoothMessage.RoundResult(scoreMap))
        sendBluetoothSnapshot()
        tvMessage.text = "本局结束。点击“下一局”继续累计比分。"
        render()
    }

    private fun render() {
        if (!::controller.isInitialized) return

        val current = currentPlayer()
        tvStatus.text = buildString {
            append("当前：${current.name}")
            append("    规则：${controller.ruleProfile.displayName}")
            append("    局数：${roundNumber}")
            append("    已过牌：${controller.state.passCount}")
            append("    Seed：${controller.state.roundSeed}")
            if (bluetoothRole != BluetoothRole.LOCAL) {
                append("    你的座位：${localPlayerId}")
            }
        }

        tvLastPlay.text = controller.state.lastPlay?.let {
            "上一手：${typeName(it.type)} ${cardsLabel(it.cards)}"
        } ?: "上一手：无，可以任意合法出牌"

        renderPlayers()
        renderRoomState()
        renderHand()
        renderSelection()
        renderLog()
        renderTablePage()

        val humanTurn = !roundOver && current.id == localPlayerId
        btnPlay.isEnabled = humanTurn && selectedCards.isNotEmpty() && !waitingForHost
        btnPass.isEnabled = humanTurn && RuleEngine.canPass(controller.state) && !waitingForHost
        btnHint.isEnabled = humanTurn && !waitingForHost
    }

    private fun renderTablePage() {
        if (!::roomPage.isInitialized || roomPage.visibility != View.VISIBLE) return

        val modeLabel = when (bluetoothRole) {
            BluetoothRole.LOCAL -> "AI 对局"
            BluetoothRole.HOST -> if (roomGameStarted) "房主对局中" else "等待真人蓝牙接入"
            BluetoothRole.CLIENT -> if (roomGameStarted) "蓝牙对局中" else "等待房主开局"
        }
        tvTableStatus.text = buildString {
            append("锄大地")
            append("    房间：${currentRoomId.ifEmpty { "本地房间" }}")
            if (::controller.isInitialized) {
                append("    ${controller.ruleProfile.displayName}")
                append("    第 ${roundNumber} 局")
                append("    已过 ${controller.state.passCount}")
            }
            append("    底：1    倍：1    $modeLabel")
        }

        if (!roomGameStarted || !::controller.isInitialized) {
            tvTableLastPlay.text = "房间准备中"
            tableLastPlayCards.removeAllViews()
            addTablePlaceholder("等待开局")
            tvTableMessage.text = waitingHumanSeats().takeIf { it.isNotEmpty() }?.let {
                "等待：${it.joinToString("、")}"
            } ?: "座位已准备，可以开始"
            tableCardContainer.removeAllViews()
            renderTableSeats()
            renderLocalPlayerHud()
            setTableActionButtons(false, false, false)
            return
        }

        tvTableLastPlay.text = controller.state.lastPlay?.let {
            "上一手：${typeName(it.type)}"
        } ?: "上一手为空"
        renderTableLastPlayCards()
        tvTableMessage.text = tvMessage.text
        renderTableSeats()
        renderLocalPlayerHud()
        renderTableHand()

        val humanTurn = !roundOver && currentPlayer().id == localPlayerId
        setTableActionButtons(
            playEnabled = humanTurn && selectedCards.isNotEmpty() && !waitingForHost,
            passEnabled = humanTurn && RuleEngine.canPass(controller.state) && !waitingForHost,
            hintEnabled = humanTurn && !waitingForHost
        )
    }

    private fun renderTableSeats() {
        tableTopSeats.removeAllViews()
        tableLeftSeats.removeAllViews()
        tableRightSeats.removeAllViews()
        val seats = if (roomGameStarted && ::controller.isInitialized) controller.state.players else createPlayers()
        seats.forEachIndexed { index, player ->
            if (player.id == localPlayerId) return@forEachIndexed
            val isCurrent = roomGameStarted && ::controller.isInitialized && player.id == currentPlayer().id && !roundOver
            val joined = player.id in roomPlayers
            val needsBluetooth = player.id == HUMAN_ID || player.id in bluetoothHumanSeats
            val seatView = createTableSeatView(player, index, isCurrent, joined, needsBluetooth)
            when (player.id) {
                "p2" -> tableLeftSeats.addView(
                    seatView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                "p3" -> tableTopSeats.addView(
                    seatView,
                    LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.MATCH_PARENT)
                )
                "p4" -> tableRightSeats.addView(
                    seatView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
    }

    private fun createTableSeatView(
        player: PlayerState,
        index: Int,
        isCurrent: Boolean,
        joined: Boolean,
        needsBluetooth: Boolean
    ): LinearLayout {
        val handText = if (roomGameStarted && ::controller.isInitialized) {
            "${player.handCards.size} 张"
        } else {
            "待开局"
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            clipToPadding = false
            setPadding(dp(7), dp(6), dp(7), dp(7))
            elevation = if (isCurrent) dp(8).toFloat() else dp(3).toFloat()
            background = roundedBackground(
                fillColor = if (isCurrent) "#E63366B0" else "#C818315F",
                strokeColor = if (isCurrent) "#FFFFDE5B" else "#8FD5EEFF",
                radiusDp = 8
            )
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(avatarResourceForSeat(player.id))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = roundedBackground("#F4F9FFFF", "#FFFFF3B4", radiusDp = 28)
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                },
                LinearLayout.LayoutParams(dp(50), dp(50))
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = displayNameForSeat(player.id, index)
                    gravity = android.view.Gravity.CENTER
                    includeFontPadding = false
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#FFFFFFFF"))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(4)
                }
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = "$handText  ·  ${seatStateLabel(player.id, joined, needsBluetooth)}"
                    gravity = android.view.Gravity.CENTER
                    includeFontPadding = false
                    textSize = 11f
                    setTextColor(Color.parseColor("#DDEFFFFF"))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                }
            )
            addScorePill(this, "分 ${player.score}")
            addSeatCardPreview(this, player.handCards.size)
            if (isCurrent) {
                addView(
                    TextView(this@MainActivity).apply {
                        text = "出牌中"
                        gravity = android.view.Gravity.CENTER
                        includeFontPadding = false
                        textSize = 11f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.parseColor("#51230B"))
                        background = resources.getDrawable(R.drawable.coin_pill, theme)
                    },
                    LinearLayout.LayoutParams(dp(58), dp(22)).apply {
                        topMargin = dp(5)
                    }
                )
            }
        }
    }

    private fun renderLocalPlayerHud() {
        if (!::tableLocalHud.isInitialized) return
        tableLocalHud.removeAllViews()
        val player = if (roomGameStarted && ::controller.isInitialized) {
            humanPlayer()
        } else {
            createPlayers().first { it.id == localPlayerId }
        }
        val isCurrent = roomGameStarted && ::controller.isInitialized && player.id == currentPlayer().id && !roundOver
        tableLocalHud.background = roundedBackground(
            fillColor = if (isCurrent) "#E63366B0" else "#D7172C58",
            strokeColor = if (isCurrent) "#FFFFDE5B" else "#8BD7F2FF",
            radiusDp = 8
        )
        tableLocalHud.addView(
            ImageView(this).apply {
                setImageResource(avatarResourceForSeat(player.id))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedBackground("#F4F9FFFF", "#FFFFF3B4", radiusDp = 30)
                setPadding(dp(2), dp(2), dp(2), dp(2))
            },
            LinearLayout.LayoutParams(dp(56), dp(56))
        )
        tableLocalHud.addView(
            TextView(this).apply {
                text = "你"
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        )
        addScorePill(tableLocalHud, "分 ${player.score}")
        tableLocalHud.addView(
            TextView(this).apply {
                text = if (roomGameStarted && ::controller.isInitialized) "手牌 ${player.handCards.size}" else "待开局"
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
                textSize = 11f
                setTextColor(Color.parseColor("#E5F7FFFF"))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        )
    }

    private fun renderTableLastPlayCards() {
        tableLastPlayCards.removeAllViews()
        val lastPlay = controller.state.lastPlay
        if (lastPlay == null) {
            addTablePlaceholder("可任意合法出牌")
            return
        }
        lastPlay.cards.sorted().forEach { card ->
            tableLastPlayCards.addView(
                miniCardView(card, textSize = 12f),
                LinearLayout.LayoutParams(dp(34), dp(50)).apply {
                    marginEnd = dp(4)
                }
            )
        }
    }

    private fun addTablePlaceholder(text: String) {
        tableLastPlayCards.addView(
            TextView(this).apply {
                this.text = text
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
                textSize = 13f
                setTextColor(Color.parseColor("#E6F7FFFF"))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun miniCardView(card: Card, textSize: Float): TextView {
        return TextView(this).apply {
            text = cardButtonLabel(card)
            gravity = android.view.Gravity.CENTER
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            this.textSize = textSize
            setTextColor(cardTextColor(card.suit))
            background = resources.getDrawable(R.drawable.card_face_table, theme)
            elevation = dp(2).toFloat()
        }
    }

    private fun addScorePill(parent: LinearLayout, text: String) {
        parent.addView(
            TextView(this).apply {
                this.text = text
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#4E2608"))
                background = resources.getDrawable(R.drawable.coin_pill, theme)
                setPadding(dp(8), 0, dp(8), 0)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(22)
            ).apply {
                topMargin = dp(5)
            }
        )
    }

    private fun addSeatCardPreview(parent: LinearLayout, cardCount: Int) {
        if (!roomGameStarted || cardCount <= 0) return
        parent.addView(
            LinearLayout(this).apply {
                gravity = android.view.Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
                clipToPadding = false
                repeat(minOf(4, cardCount)) { previewIndex ->
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(R.drawable.card_back_kenney)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            alpha = 0.96f
                        },
                        LinearLayout.LayoutParams(dp(24), dp(28)).apply {
                            if (previewIndex > 0) marginStart = -dp(7)
                        }
                    )
                }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(5)
            }
        )
    }

    private fun avatarResourceForSeat(playerId: String): Int {
        return when (playerId) {
            HUMAN_ID -> R.drawable.avatar_player
            "p2" -> R.drawable.avatar_ai_2
            "p3" -> R.drawable.avatar_ai_3
            "p4" -> R.drawable.avatar_ai_4
            else -> R.drawable.avatar_ai_2
        }
    }

    private fun seatStateLabel(playerId: String, joined: Boolean, needsBluetooth: Boolean): String {
        return when {
            playerId == localPlayerId -> "你"
            joined -> "真人"
            needsBluetooth -> "等待"
            else -> "AI"
        }
    }

    private fun renderTableHand() {
        tableCardContainer.removeAllViews()
        val humanTurn = !roundOver && currentPlayer().id == localPlayerId

        val cards = sortHand(humanPlayer().handCards)
        cards.forEach { card ->
            val selected = card in selectedCards
            val button = Button(this).apply {
                text = cardButtonLabel(card)
                setAllCaps(false)
                includeFontPadding = false
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                minWidth = dp(TABLE_CARD_WIDTH_DP)
                minHeight = dp(100)
                setTextColor(cardTextColor(card.suit))
                setPadding(dp(3), dp(3), dp(3), dp(3))
                isEnabled = humanTurn
                elevation = if (selected) dp(10).toFloat() else dp(4).toFloat()
                translationY = if (selected) -dp(16).toFloat() else 0f
                background = if (selected) {
                    roundedBackground(
                        fillColor = "#FFFFE4A6",
                        strokeColor = "#FFF1C45B",
                        radiusDp = 7
                    )
                } else {
                    resources.getDrawable(R.drawable.card_face_table, theme)
                }
                setOnClickListener {
                    playUiSound(selectSoundId)
                    if (card in selectedCards) {
                        selectedCards.remove(card)
                    } else {
                        selectedCards.add(card)
                    }
                    render()
                }
            }
            val params = LinearLayout.LayoutParams(dp(TABLE_CARD_WIDTH_DP), dp(110)).apply {
                marginEnd = compactCardSpacing(cards.size, TABLE_CARD_WIDTH_DP, TABLE_RESERVED_WIDTH_DP)
            }
            tableCardContainer.addView(button, params)
        }
    }

    private fun setTableActionButtons(playEnabled: Boolean, passEnabled: Boolean, hintEnabled: Boolean) {
        btnTablePlay.isEnabled = playEnabled
        btnTablePass.isEnabled = passEnabled
        btnTableHint.isEnabled = hintEnabled
    }

    private fun renderPlayers() {
        playerBoard.removeAllViews()
        controller.state.players.forEach { player ->
            val isCurrent = player.id == currentPlayer().id && !roundOver
            val hasFinished = player.id in controller.state.finishOrder
            val textView = TextView(this).apply {
                text = buildString {
                    append(if (isCurrent) "▶ " else "   ")
                    append(player.name)
                    append(if (player.id == localPlayerId) "（你）" else "")
                    append("  手牌 ${player.handCards.size}")
                    append("  分数 ${player.score}")
                    if (hasFinished) append("  已出完")
                }
                textSize = 15f
                setTextColor(Color.parseColor("#1D2B25"))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBackground(
                    fillColor = if (isCurrent) "#CBEAD1" else "#FFFFFF",
                    strokeColor = if (isCurrent) "#3B8A55" else "#D8E0DA"
                )
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
            playerBoard.addView(textView, params)
        }
    }

    private fun broadcastRoomState() {
        if (bluetoothRole == BluetoothRole.LOCAL) return
        syncManager?.sendMessage(
            BluetoothMessage.RoomState(
                players = roomPlayers.toList(),
                readyPlayers = readyPlayers.filter { it in roomPlayers },
                bluetoothPlayers = bluetoothHumanSeats.toList()
            )
        )
        renderRoomState()
    }

    private fun toggleSeatMode(playerId: String) {
        if (bluetoothRole != BluetoothRole.HOST || playerId == localPlayerId) return
        if (playerId in roomPlayers) {
            tvBluetoothStatus.text = "$playerId 已有真人加入，断开后才能改成 AI。"
            addLog("蓝牙：$playerId 已加入，暂不能切换座位类型")
            renderLog()
            return
        }

        if (playerId in bluetoothHumanSeats) {
            bluetoothHumanSeats.remove(playerId)
            readyPlayers.remove(playerId)
            clientSeatByRequestId.entries.removeAll { it.value == playerId }
            addLog("房主设置：$playerId 由 AI 托管")
        } else {
            bluetoothHumanSeats.add(playerId)
            addLog("房主设置：$playerId 需要蓝牙真人")
        }
        broadcastRoomState()
        renderLog()
    }

    private fun renderRoomState() {
        if (!::roomStateBoard.isInitialized) return
        roomStateBoard.removeAllViews()
        if (bluetoothRole == BluetoothRole.LOCAL) return

        PLAYER_IDS.forEachIndexed { index, playerId ->
            val joined = playerId in roomPlayers
            val ready = playerId in readyPlayers
            val owner = playerId == localPlayerId
            val needsBluetooth = playerId == HUMAN_ID || playerId in bluetoothHumanSeats
            val textView = TextView(this).apply {
                text = buildString {
                    append(playerId)
                    append("  ")
                    append(
                        when {
                            owner -> "你"
                            playerId == HUMAN_ID -> "房主"
                            joined -> "蓝牙真人已加入"
                            needsBluetooth -> "等待蓝牙真人"
                            else -> "AI 托管"
                        }
                    )
                    append("  ")
                    append(
                        when {
                            !needsBluetooth -> "无需准备"
                            ready -> "已准备"
                            else -> "未准备"
                        }
                    )
                    if (index == 0) append("  主机")
                }
                textSize = 14f
                setTextColor(Color.parseColor("#243142"))
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = roundedBackground(
                    fillColor = if (owner) "#E6F4EA" else "#FFFFFF",
                    strokeColor = when {
                        ready -> "#3B8A55"
                        needsBluetooth -> "#D59E2A"
                        else -> "#CBD5E1"
                    }
                )
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    textView,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                if (bluetoothRole == BluetoothRole.HOST && playerId != HUMAN_ID) {
                    addView(
                        Button(this@MainActivity).apply {
                            text = if (needsBluetooth) "设为AI" else "设为真人"
                            setAllCaps(false)
                            minHeight = dp(40)
                            setTextColor(Color.parseColor("#FFF4D6"))
                            background = resources.getDrawable(R.drawable.button_secondary, theme)
                            isEnabled = !joined
                            setOnClickListener {
                                playUiSound(selectSoundId)
                                toggleSeatMode(playerId)
                            }
                        },
                        LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(6)
                        }
                    )
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            roomStateBoard.addView(row, params)
        }
    }

    private fun renderHand() {
        cardContainer.removeAllViews()
        val humanTurn = !roundOver && currentPlayer().id == localPlayerId

        val cards = sortHand(humanPlayer().handCards)
        cards.forEach { card ->
            val selected = card in selectedCards
            val button = Button(this).apply {
                text = cardButtonLabel(card)
                setAllCaps(false)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                minWidth = dp(COMPACT_CARD_WIDTH_DP)
                minHeight = dp(76)
                setTextColor(cardTextColor(card.suit))
                setPadding(dp(4), dp(4), dp(4), dp(4))
                isEnabled = humanTurn
                elevation = if (selected) dp(7).toFloat() else dp(2).toFloat()
                translationY = if (selected) -dp(8).toFloat() else 0f
                background = roundedBackground(
                    fillColor = if (selected) "#FFE4A6" else "#FFFDF2",
                    strokeColor = if (selected) "#F1C45B" else "#D3A663",
                    radiusDp = 8
                )
                setOnClickListener {
                    playUiSound(selectSoundId)
                    if (card in selectedCards) {
                        selectedCards.remove(card)
                    } else {
                        selectedCards.add(card)
                    }
                    render()
                }
            }
            val params = LinearLayout.LayoutParams(dp(COMPACT_CARD_WIDTH_DP), dp(88)).apply {
                marginEnd = compactCardSpacing(cards.size, COMPACT_CARD_WIDTH_DP, SETUP_RESERVED_WIDTH_DP)
            }
            cardContainer.addView(button, params)
        }
    }

    private fun renderSelection() {
        tvSelection.text = if (selectedCards.isEmpty()) {
            "未选择手牌"
        } else {
            val cards = selectedCards.toList().sorted()
            val play = HandEvaluator.evaluate(cards, controller.ruleProfile)
            val type = play?.type?.let { typeName(it) } ?: "非法牌型"
            "已选：$type ${cardsLabel(cards)}"
        }
    }

    private fun renderLog() {
        tvLog.text = if (logLines.isEmpty()) {
            "对局日志会显示在这里。"
        } else {
            logLines.takeLast(10).joinToString("\n")
        }
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        btnPlay.isEnabled = enabled
        btnPass.isEnabled = enabled
        btnHint.isEnabled = enabled
    }

    private fun invalidPlayMessage(cards: List<Card>): String {
        val state = controller.state
        return when {
            state.firstRound &&
                controller.ruleProfile.firstRoundMustContainDiamondThree &&
                Card.DIAMOND_THREE !in cards -> "南方规则首轮必须包含方块 3。"
            state.lastPlay != null && state.lastPlay?.cards?.size != cards.size -> "需要出和上一手相同张数的牌。"
            else -> "这手牌压不过上一手，或者现在还不能这样出。"
        }
    }

    private fun currentPlayer(): PlayerState {
        return controller.state.players[controller.state.currentPlayerIndex]
    }

    private fun humanPlayer(): PlayerState {
        return controller.state.players.first { it.id == localPlayerId }
    }

    private fun strategyFor(playerId: String): PlayStrategy {
        return if (playerId == "p3") ConservativeStrategy() else GreedyStrategy()
    }

    private fun shouldWaitForRemotePlayer(): Boolean {
        val currentId = currentPlayer().id
        return when (bluetoothRole) {
            BluetoothRole.LOCAL -> false
            BluetoothRole.CLIENT -> currentId != localPlayerId
            BluetoothRole.HOST -> currentId != localPlayerId && currentId in roomPlayers
        }
    }

    private fun allJoinedPlayersReady(): Boolean {
        return requiredHumanSeats().all { playerId ->
            playerId in roomPlayers && playerId in readyPlayers
        }
    }

    private fun requiredHumanSeats(): List<String> {
        return if (bluetoothRole == BluetoothRole.HOST) {
            buildList {
                add(localPlayerId)
                addAll(bluetoothHumanSeats)
            }
        } else {
            roomPlayers.toList()
        }
    }

    private fun waitingHumanSeats(): List<String> {
        return requiredHumanSeats().filter { playerId ->
            playerId !in roomPlayers || playerId !in readyPlayers
        }
    }

    private fun noteRemoteActivity(playerId: String, timestamp: Long = System.currentTimeMillis()) {
        if (playerId.isBlank() || playerId == localPlayerId) return
        lastHeartbeatByPlayer[playerId] = timestamp
        lastHeartbeatAt = timestamp
        heartbeatTimeoutReported = false
    }

    private fun checkHeartbeatTimeout(now: Long) {
        if (bluetoothRole == BluetoothRole.HOST) {
            val timedOutPlayers = roomPlayers
                .filter { it != localPlayerId }
                .filter { playerId ->
                    val lastSeen = lastHeartbeatByPlayer[playerId] ?: return@filter false
                    now - lastSeen > HEARTBEAT_TIMEOUT_MS
                }

            timedOutPlayers.forEach { playerId ->
                markPlayerOffline(
                    playerId = playerId,
                    reason = "心跳超时 ${now - (lastHeartbeatByPlayer[playerId] ?: now)}ms",
                    broadcast = true
                )
            }
            return
        }

        if (lastHeartbeatAt == 0L || heartbeatTimeoutReported) return
        if (now - lastHeartbeatAt <= HEARTBEAT_TIMEOUT_MS) return

        heartbeatTimeoutReported = true
        tvBluetoothStatus.text = "蓝牙心跳超时，请检查连接或点击重连。"
        addLog("蓝牙：心跳超时，距离上次心跳 ${now - lastHeartbeatAt}ms")
        renderLog()
    }

    private fun markPlayerOffline(playerId: String, reason: String, broadcast: Boolean) {
        val seatId = canonicalPlayerId(playerId)
        readyPlayers.remove(seatId)
        lastHeartbeatByPlayer.remove(seatId)
        clientSeatByRequestId.entries.removeAll { it.value == seatId }
        val wasJoined = roomPlayers.remove(seatId)

        if (wasJoined || seatId != playerId) {
            addLog("蓝牙：$seatId 离线（$reason）")
        } else {
            addLog("蓝牙：$playerId 离线（$reason）")
        }

        if (bluetoothRole == BluetoothRole.HOST && broadcast && seatId in PLAYER_IDS) {
            syncManager?.sendMessage(BluetoothMessage.PlayerOffline(seatId))
            broadcastRoomState()
            if (::controller.isInitialized) {
                sendBluetoothSnapshot()
                runAiTurns()
            }
        }

        renderRoomState()
    }

    private fun canonicalPlayerId(playerId: String): String {
        return clientSeatByRequestId[playerId] ?: playerId
    }

    private fun assignSeat(requestPlayerId: String): String? {
        clientSeatByRequestId[requestPlayerId]?.let { return it }
        val usedSeats = roomPlayers + clientSeatByRequestId.values
        val seat = bluetoothHumanSeats.firstOrNull { it !in usedSeats } ?: return null
        clientSeatByRequestId[requestPlayerId] = seat
        return seat
    }

    private fun displayNameForSeat(playerId: String, index: Int): String {
        if (playerId == localPlayerId) return "你"
        return when (bluetoothRole) {
            BluetoothRole.LOCAL -> "AI ${index + 1}"
            BluetoothRole.HOST -> if (playerId in roomPlayers) "玩家 ${index + 1}" else "AI ${index + 1}"
            BluetoothRole.CLIENT -> when {
                playerId == HUMAN_ID -> "主机"
                playerId in bluetoothHumanSeats -> "玩家 ${index + 1}"
                else -> "AI ${index + 1}"
            }
        }
    }

    private fun isAiSeat(playerId: String): Boolean {
        return when (bluetoothRole) {
            BluetoothRole.LOCAL -> playerId != localPlayerId
            BluetoothRole.HOST -> playerId != localPlayerId && playerId !in roomPlayers
            BluetoothRole.CLIENT -> false
        }
    }

    private fun createPlayers(): List<PlayerState> {
        return PLAYER_IDS.mapIndexed { index, playerId ->
            PlayerState(
                id = playerId,
                name = displayNameForSeat(playerId, index),
                isAi = isAiSeat(playerId),
                score = matchScores[playerId] ?: 0
            )
        }
    }

    private fun sendBluetoothSnapshot() {
        if (!::controller.isInitialized) return
        val state = controller.state
        syncManager?.sendMessage(
            BluetoothMessage.GameStateSnapshot(
                seed = state.roundSeed,
                currentPlayerId = currentPlayer().id,
                lastPlayCards = state.lastPlay?.cards?.let(CardWireCodec::encodeList) ?: emptyList(),
                hands = emptyMap(),
                handCounts = state.players.associate { it.id to it.handCards.size },
                scores = state.players.associate { it.id to it.score },
                finishOrder = state.finishOrder.toList(),
                passCount = state.passCount,
                firstRound = state.firstRound,
                lastWinnerId = state.lastWinnerId
            )
        )
        if (bluetoothRole == BluetoothRole.HOST) {
            sendPrivateHandsToRemotePlayers()
        }
    }

    private fun sendPrivateHandsToRemotePlayers() {
        controller.state.players.forEach { player ->
            if (player.id != localPlayerId && player.id in roomPlayers) {
                val sent = ensureSyncManager().sendMessageToPlayer(
                    player.id,
                    BluetoothMessage.PrivateHand(
                        playerId = player.id,
                        cards = CardWireCodec.encodeList(player.handCards)
                    )
                )
                if (!sent) {
                    markPlayerOffline(
                        playerId = player.id,
                        reason = "私人手牌发送失败",
                        broadcast = true
                    )
                }
            }
        }
    }

    private fun roomIdFromInput(defaultValue: String): String {
        return etBluetoothRoom.text.toString().trim().ifEmpty { defaultValue }
    }

    private fun randomRoomId(): String {
        return (100000..999999).random().toString()
    }

    private fun sortHand(cards: List<Card>): List<Card> {
        return cards.sorted()
    }

    private fun compactCardSpacing(cardCount: Int, cardWidthDp: Int, reservedWidthDp: Int): Int {
        if (cardCount <= 1) return 0
        val cardWidth = dp(cardWidthDp)
        val availableWidth = (resources.displayMetrics.widthPixels - dp(reservedWidthDp)).coerceAtLeast(dp(280))
        val naturalWidth = cardWidth * cardCount
        if (naturalWidth <= availableWidth) return dp(4)

        val overlap = ((naturalWidth - availableWidth) / (cardCount - 1)).coerceAtMost(cardWidth - dp(24))
        return -overlap
    }

    private fun playerName(playerId: String): String {
        return controller.state.players.firstOrNull { it.id == playerId }?.name ?: playerId
    }

    private fun addLog(text: String) {
        logLines.add(text)
        if (logLines.size > 40) logLines.removeAt(0)
    }

    private fun cardsLabel(cards: List<Card>): String {
        return cards.sorted().joinToString(" ") { "${suitSymbol(it.suit)}${rankLabel(it.rank)}" }
    }

    private fun cardButtonLabel(card: Card): String {
        return "${suitSymbol(card.suit)}\n${rankLabel(card.rank)}"
    }

    private fun rankLabel(rank: Rank): String {
        return when (rank) {
            Rank.THREE -> "3"
            Rank.FOUR -> "4"
            Rank.FIVE -> "5"
            Rank.SIX -> "6"
            Rank.SEVEN -> "7"
            Rank.EIGHT -> "8"
            Rank.NINE -> "9"
            Rank.TEN -> "10"
            Rank.JACK -> "J"
            Rank.QUEEN -> "Q"
            Rank.KING -> "K"
            Rank.ACE -> "A"
            Rank.TWO -> "2"
        }
    }

    private fun suitSymbol(suit: Suit): String {
        return when (suit) {
            Suit.DIAMOND -> "♦"
            Suit.CLUB -> "♣"
            Suit.HEART -> "♥"
            Suit.SPADE -> "♠"
        }
    }

    private fun cardTextColor(suit: Suit): Int {
        return when (suit) {
            Suit.DIAMOND,
            Suit.HEART -> Color.parseColor("#B72A2A")
            Suit.CLUB,
            Suit.SPADE -> Color.parseColor("#1D1D1D")
        }
    }

    private fun typeName(type: HandType?): String {
        return when (type) {
            HandType.SINGLE -> "单张"
            HandType.PAIR -> "对子"
            HandType.TRIPLE -> "三条"
            HandType.BOMB4 -> "四炸"
            HandType.STRAIGHT -> "顺子"
            HandType.FLUSH5 -> "同花五"
            HandType.FULL_HOUSE -> "葫芦"
            HandType.FOUR_PLUS_ONE -> "铁支"
            HandType.STRAIGHT_FLUSH -> "同花顺"
            null -> "未知"
        }
    }

    private fun statusLabel(state: BluetoothConnectionState): String {
        return when (state) {
            BluetoothConnectionState.IDLE -> "空闲"
            BluetoothConnectionState.HOSTING -> "创建房间中"
            BluetoothConnectionState.CONNECTING -> "连接中"
            BluetoothConnectionState.CONNECTED -> "已连接"
            BluetoothConnectionState.DISCONNECTED -> "已断开"
            BluetoothConnectionState.ERROR -> "错误"
        }
    }

    private fun roundedBackground(
        fillColor: String,
        strokeColor: String,
        radiusDp: Int = 6
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillColor))
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.parseColor(strokeColor))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val HUMAN_ID = "p1"
        private const val COMPACT_CARD_WIDTH_DP = 48
        private const val TABLE_CARD_WIDTH_DP = 62
        private const val SETUP_RESERVED_WIDTH_DP = 32
        private const val TABLE_RESERVED_WIDTH_DP = 144
        private const val AI_TURN_DELAY_MS = 700L
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val HEARTBEAT_TIMEOUT_MS = 15_000L
        private val PLAYER_IDS = listOf("p1", "p2", "p3", "p4")
    }

    private enum class BluetoothRole {
        LOCAL,
        HOST,
        CLIENT
    }
}
