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
import android.content.Intent
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.scut.chudadi.rule.RuleProfiles

/**
 * 游戏主页面。
 *
 * 当前采用单 Activity 承载大厅、蓝牙房间和牌桌界面；核心牌局状态交给 GameController，
 * 本类主要负责 Android UI 渲染、用户输入、AI 调度和蓝牙消息编排。
 */
class MainActivity : AppCompatActivity() {
    /** 旧版调试/紧凑界面的主要控件，仍用于状态、日志和备用手牌渲染。 */
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
    // 新大厅的入口是整张卡片，所以用 View 绑定外层容器，而不是只绑定 Button。
    private lateinit var btnNewGame: View
    /** 蓝牙房间和大厅配置控件。 */
    private lateinit var etBluetoothRoom: EditText
    private lateinit var tvBluetoothStatus: TextView
    private lateinit var btnBluetoothBack: Button
    private lateinit var btnBluetoothPermission: Button
    private lateinit var btnBluetoothDevices: Button
    private lateinit var btnBluetoothReady: Button
    private lateinit var btnBluetoothJoin: View
    private lateinit var pairedDeviceBoard: LinearLayout
    private lateinit var roomStateBoard: LinearLayout
    private lateinit var setupPage: View
    private lateinit var roomPage: FrameLayout
    /** 牌桌页控件，进入房间后由 renderTablePage 统一刷新。 */
    private lateinit var tvTableStatus: TextView
    private lateinit var tvTableMessage: TextView
    private lateinit var tableTopSeats: LinearLayout
    private lateinit var tableLeftSeats: LinearLayout
    private lateinit var tableRightSeats: LinearLayout
    private lateinit var tableLocalHud: LinearLayout
    private lateinit var tableLocalLastPlay: LinearLayout
    private lateinit var tableCardContainer: LinearLayout
    private lateinit var btnTablePlay: Button
    private lateinit var tvTableCountdown: TextView
    private lateinit var btnTablePass: Button
    private lateinit var btnTableHint: Button
    private lateinit var btnTableNewGame: TextView
    private lateinit var btnTableLeaveRoom: TextView
    private lateinit var rbModeLocal: RadioButton
    private lateinit var rbModeBluetooth: RadioButton
    private lateinit var rbHumans2: RadioButton
    private lateinit var rbHumans3: RadioButton
    private lateinit var rbHumans4: RadioButton
    private lateinit var rbSouth: RadioButton
    private lateinit var rbNorth: RadioButton
    // “离线模式”同样是卡片容器，点击范围覆盖图片和文字。
    private lateinit var btnEnterTable: View
    private lateinit var bluetoothRoomPanel: View
    private lateinit var roomStatePanel: View
    private lateinit var tvSetupSummary: TextView

    /** 本局游戏控制器，startNewGame 后才会初始化。 */
    private lateinit var controller: GameController
    /** 跨多局累计的比赛分数。 */
    private val matchScores = mutableMapOf<String, Int>()
    /** 客户端不能知道全部手牌时，用公共快照里的手牌数量覆盖本地显示。 */
    private val visibleHandCounts = mutableMapOf<String, Int>()
    /** 当前本地玩家点选的手牌，使用 LinkedHashSet 保持选择顺序且避免重复。 */
    private val selectedCards = linkedSetOf<Card>()
    /** 对局和蓝牙状态日志，最多保留最近 40 条。 */
    private val logLines = mutableListOf<String>()
    /** UI 定时任务统一走主线程 Handler，包括 AI 延迟、心跳和回合倒计时。 */
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var soundPool: SoundPool
    private var selectSoundId = 0
    private var playSoundId = 0
    private var passSoundId = 0
    private var errorSoundId = 0
    /** 当前蓝牙连接管理器；本地模式下为空。 */
    private var syncManager: BluetoothGameSyncManager? = null
    /** 已加入房间的正式座位。 */
    private val roomPlayers = linkedSetOf(HUMAN_ID)
    /** 已准备座位；房主会根据它判断是否自动开局。 */
    private val readyPlayers = linkedSetOf(HUMAN_ID)
    /** 房主配置为“需要蓝牙真人”的远端座位。 */
    private val bluetoothHumanSeats = linkedSetOf<String>()
    /** 房主记录远端座位最近一次活动时间，用于心跳超时检测。 */
    private val lastHeartbeatByPlayer = mutableMapOf<String, Long>()
    /** 临时 guest id 到正式座位 id 的映射。 */
    private val clientSeatByRequestId = mutableMapOf<String, String>()
    /** 当前设备在联机流程中的角色。 */
    private var bluetoothRole = BluetoothRole.LOCAL
    /** 本机在牌局中的正式座位 id。 */
    private var localPlayerId = HUMAN_ID
    /** 蓝牙连接建立时使用的玩家 id，加入前可能是 guest id。 */
    private var networkPlayerId = HUMAN_ID
    /** syncManager 当前绑定的 owner id，切换 guest/正式座位时用来判断是否重建。 */
    private var syncManagerOwnerId: String? = null
    private var currentRoomId = ""
    private var lastWinnerId: String? = null
    private var waitingForHost = false
    private var roomGameStarted = false
    private var lastHeartbeatAt = 0L
    private var heartbeatTimeoutReported = false
    private var roundNumber = 0
    private var roundOver = false
    private var selectedRuleSetType = RuleSetType.SOUTH
    private var shouldFadeNextTablePrompt = false
    private var lastTablePromptText: String? = null
    private var turnTimerPlayerId: String? = null
    private var turnTimerDeadlineAt = 0L

    /** 联机保活任务：发送心跳、检测超时，房主还会周期性广播快照纠偏。 */
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (bluetoothRole != BluetoothRole.LOCAL) {
                val now = System.currentTimeMillis()
                syncManager?.sendMessage(BluetoothMessage.Heartbeat(now, localPlayerId))
                checkHeartbeatTimeout(now)
                if (
                    bluetoothRole == BluetoothRole.HOST &&
                    roomGameStarted &&
                    ::controller.isInitialized
                ) {
                    sendBluetoothSnapshot()
                }
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /** 每秒刷新当前回合倒计时，到时后根据局面自动过牌或出牌。 */
    private val turnCountdownRunnable = object : Runnable {
        override fun run() {
            if (!::controller.isInitialized || !roomGameStarted || roundOver) {
                stopTurnCountdown()
                return
            }

            val timerPlayerId = turnTimerPlayerId
            if (timerPlayerId == null || timerPlayerId != currentPlayer().id) {
                syncTurnCountdown(reset = true)
                return
            }

            updateTurnCountdownUi()
            if (System.currentTimeMillis() >= turnTimerDeadlineAt) {
                handleTurnTimeout(timerPlayerId)
                return
            }

            handler.postDelayed(this, TURN_COUNTDOWN_TICK_MS)
        }
    }

    /** 蓝牙权限申请回调，只负责更新状态提示，实际连接由按钮流程重新触发。 */
    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val granted = BluetoothPermissionHelper.hasRequiredPermissions(this)
            tvBluetoothStatus.text = if (granted) {
                "蓝牙权限已授权，可以选择已配对设备。"
            } else {
                "蓝牙权限未完全授权，无法联机。"
            }
        }

    /** 初始化页面、控件绑定、事件监听和大厅默认状态。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_main)
        // 横屏全屏显示，避免系统状态栏在大厅顶部露出白条。
        setTableImmersiveMode(enabled = true)

        // 初始化本地偏好和音乐管理器；二者使用 applicationContext，避免持有 Activity。
        UserPrefs.init(applicationContext)
        com.scut.chudadi.audio.MusicManager.init(applicationContext)

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
        etBluetoothRoom = findViewById(R.id.etBluetoothRoom)
        tvBluetoothStatus = findViewById(R.id.tvBluetoothStatus)
        btnBluetoothBack = findViewById(R.id.btnBluetoothBack)
        btnBluetoothPermission = findViewById(R.id.btnBluetoothPermission)
        btnBluetoothDevices = findViewById(R.id.btnBluetoothDevices)
        btnBluetoothReady = findViewById(R.id.btnBluetoothReady)
        btnBluetoothJoin = findViewById(R.id.btnBluetoothJoin)
        pairedDeviceBoard = findViewById(R.id.pairedDeviceBoard)
        roomStateBoard = findViewById(R.id.roomStateBoard)
        setupPage = findViewById(R.id.setupPage)
        roomPage = findViewById(R.id.roomPage)
        tvTableStatus = findViewById(R.id.tvTableStatus)
        tvTableMessage = findViewById(R.id.tvTableMessage)
        tableTopSeats = findViewById(R.id.tableTopSeats)
        tableLeftSeats = findViewById(R.id.tableLeftSeats)
        tableRightSeats = findViewById(R.id.tableRightSeats)
        tableLocalHud = findViewById(R.id.tableLocalHud)
        tableLocalLastPlay = findViewById(R.id.tableLocalLastPlay)
        tableCardContainer = findViewById(R.id.tableCardContainer)
        btnTablePlay = findViewById(R.id.btnTablePlay)
        tvTableCountdown = findViewById(R.id.tvTableCountdown)
        btnTablePass = findViewById(R.id.btnTablePass)
        btnTableHint = findViewById(R.id.btnTableHint)
        btnTableNewGame = findViewById(R.id.btnTableNewGame)
        btnTableLeaveRoom = findViewById(R.id.btnTableLeaveRoom)
        rbModeLocal = findViewById(R.id.rbModeLocal)
        rbModeBluetooth = findViewById(R.id.rbModeBluetooth)
        rbHumans2 = findViewById(R.id.rbHumans2)
        rbHumans3 = findViewById(R.id.rbHumans3)
        rbHumans4 = findViewById(R.id.rbHumans4)
        rbSouth = findViewById(R.id.rbSouth)
        rbNorth = findViewById(R.id.rbNorth)
        btnEnterTable = findViewById(R.id.btnEnterTable)
        bluetoothRoomPanel = findViewById(R.id.bluetoothRoomPanel)
        roomStatePanel = findViewById(R.id.roomStatePanel)
        tvSetupSummary = findViewById(R.id.tvSetupSummary)

        initAudioFeedback()

        // 大厅三张主卡片分别对应：创建蓝牙房间、加入蓝牙房间、离线开局。
        btnNewGame.setOnClickListener {
            playUiSound(selectSoundId)
            rbModeBluetooth.isChecked = true
            updateLobbySetupUi()
            startConfiguredBluetoothRoom()
        }
        btnPlay.setOnClickListener { playSelectedCards() }
        btnPass.setOnClickListener { passTurn() }
        btnHint.setOnClickListener { selectHintCards() }
        btnBluetoothBack.setOnClickListener { closeBluetoothSettingsPanel() }
        btnBluetoothPermission.setOnClickListener { requestBluetoothPermissions() }
        btnBluetoothDevices.setOnClickListener { showBondedBluetoothDevices() }
        btnBluetoothReady.setOnClickListener { markBluetoothReady() }
        btnBluetoothJoin.setOnClickListener { openBluetoothJoinFlow() }
        btnTablePlay.setOnClickListener { playSelectedCards() }
        btnTablePass.setOnClickListener { passTurn() }
        btnTableHint.setOnClickListener { selectHintCards() }
        btnTableNewGame.setOnClickListener { startNewGame() }
        btnTableLeaveRoom.setOnClickListener { leaveRoomPage() }
        findViewById<View>(R.id.btnProfile)?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        btnEnterTable.setOnClickListener {
            playUiSound(selectSoundId)
            rbModeLocal.isChecked = true
            updateLobbySetupUi()
            createLocalAiRoom()
        }
        listOf(rbModeLocal, rbModeBluetooth, rbHumans2, rbHumans3, rbHumans4, rbSouth, rbNorth).forEach { radioButton ->
            radioButton.setOnClickListener { updateLobbySetupUi() }
        }

        initializeLobbySetup()
    }

    /** 回到前台后恢复背景音乐。 */
    override fun onResume() {
        super.onResume()
        try {
            com.scut.chudadi.audio.MusicManager.instance().play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 切到后台时暂停背景音乐，但不清空牌局状态。 */
    override fun onPause() {
        super.onPause()
        try {
            com.scut.chudadi.audio.MusicManager.instance().pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 页面销毁时释放定时任务、蓝牙连接和音效池。 */
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        syncManager?.disconnect()
        if (::soundPool.isInitialized) {
            soundPool.release()
        }
        super.onDestroy()
    }

    /** 重新获得焦点后再次隐藏系统栏，防止系统手势导致牌桌退出沉浸式。 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setTableImmersiveMode(enabled = true)
        }
    }

    /** 初始化短音效池，供选牌、出牌、过牌和错误提示复用。 */
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

    /** 播放一个 UI 短音效；资源未加载时直接忽略。 */
    private fun playUiSound(soundId: Int, volume: Float = 0.55f) {
        if (!::soundPool.isInitialized || soundId == 0) return
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    /** 同步更新旧版提示区域和牌桌提示区域。 */
    private fun setGameMessage(text: String, fade: Boolean = false) {
        shouldFadeNextTablePrompt = fade
        showMessage(tvMessage, text, fade)

        if (::tvTableMessage.isInitialized) {
            showTablePrompt(text, force = true)
        }
    }

    /** 设置提示文案，并按需触发淡入淡出动画。 */
    private fun showMessage(view: TextView, text: String, fade: Boolean = false) {
        view.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.text = text
        if (fade) {
            animateFadingMessage(view)
        }
    }

    /** 提示文字淡入、短暂停留后淡出，减少重复提示占屏。 */
    private fun animateFadingMessage(view: TextView) {
        view.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.scaleX = 0.94f
        view.scaleY = 0.94f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(0L)
            .setDuration(MESSAGE_FADE_IN_MS)
            .withEndAction {
                view.animate()
                    .alpha(0f)
                    .scaleX(0.98f)
                    .scaleY(0.98f)
                    .setStartDelay(MESSAGE_HOLD_MS)
                    .setDuration(MESSAGE_FADE_OUT_MS)
                    .withEndAction {
                        view.visibility = View.GONE
                        view.scaleX = 1f
                        view.scaleY = 1f
                    }
                    .start()
            }
            .start()
    }

    /** 牌桌中央信息提示，避免相同文本反复触发动画。 */
    private fun showTablePrompt(text: String, force: Boolean = false) {
        if (!::tvTableMessage.isInitialized) return
        if (!force && text == lastTablePromptText) return
        lastTablePromptText = text
        showMessage(tvTableMessage, text, fade = true)
    }

    /** 进入或重置大厅时恢复默认 UI 状态。 */
    private fun initializeLobbySetup() {
        roomGameStarted = false
        roundOver = false
        lastTablePromptText = null
        visibleHandCounts.clear()
        selectedCards.clear()
        playerBoard.removeAllViews()
        cardContainer.removeAllViews()
        pairedDeviceBoard.removeAllViews()
        renderRoomState()
        tvStatus.text = "请选择模式和玩家数量后开始。"
        tvLastPlay.text = getString(R.string.no_last_play)
        tvSelection.text = getString(R.string.no_selection)
        tvMessage.text = "先设置玩家，再开始牌局。"
        renderLog()
        setActionButtonsEnabled(false)
        updateLobbySetupUi()
    }

    /** 完整重置本地/蓝牙房间状态，回到未开局大厅。 */
    private fun resetLobbySetup() {
        handler.removeCallbacksAndMessages(null)
        syncManager?.disconnect()
        bluetoothRole = BluetoothRole.LOCAL
        localPlayerId = HUMAN_ID
        networkPlayerId = HUMAN_ID
        syncManagerOwnerId = null
        currentRoomId = ""
        waitingForHost = false
        lastWinnerId = null
        lastHeartbeatAt = 0L
        heartbeatTimeoutReported = false
        lastTablePromptText = null
        roundNumber = 0
        matchScores.clear()
        visibleHandCounts.clear()
        logLines.clear()
        turnTimerPlayerId = null
        turnTimerDeadlineAt = 0L
        lastHeartbeatByPlayer.clear()
        bluetoothHumanSeats.clear()
        clientSeatByRequestId.clear()
        roomPlayers.clear()
        roomPlayers.add(localPlayerId)
        readyPlayers.clear()
        readyPlayers.add(localPlayerId)
        rbModeLocal.isChecked = true
        rbHumans2.isChecked = true
        rbSouth.isChecked = true
        selectedRuleSetType = RuleSetType.SOUTH
        etBluetoothRoom.setText("")
        tvBluetoothStatus.text = getString(R.string.bluetooth_idle)
        initializeLobbySetup()
    }

    /** 根据大厅选择刷新模式、真人数量、AI 数量和规则摘要。 */
    private fun updateLobbySetupUi() {
        applyRuleSelection()
        val bluetoothMode = rbModeBluetooth.isChecked
        bluetoothRoomPanel.visibility = if (bluetoothMode) View.VISIBLE else View.GONE
        roomStatePanel.visibility = if (bluetoothMode) View.VISIBLE else View.GONE

        val ruleName = selectedRuleProfileName()
        if (bluetoothMode) {
            val humanCount = selectedHumanCount()
            val aiCount = PLAYER_IDS.size - humanCount
            tvSetupSummary.text = "联机对局：$humanCount 位真人 + $aiCount 个 AI。规则：$ruleName。真人加入完成会自动开局。"
        } else {
            tvSetupSummary.text = "${getString(R.string.setup_summary_local)} 规则：$ruleName。"
        }
    }

    /** 从蓝牙设置面板返回大厅顶部。 */
    private fun closeBluetoothSettingsPanel() {
        playUiSound(selectSoundId)
        rbModeLocal.isChecked = true
        updateLobbySetupUi()
        tvMessage.text = "已返回大厅。创建房间和加入房间请使用上方卡片。"
        setupPage.post {
            (setupPage as? ScrollView)?.smoothScrollTo(0, 0)
        }
    }

    private fun scrollToBluetoothSettingsPanel() {
        // 蓝牙设置面板在大厅卡片下方，小屏横屏时需要主动滚动到它的位置。
        setupPage.post {
            val targetTop = (bluetoothRoomPanel.top - dp(12)).coerceAtLeast(0)
            (setupPage as? ScrollView)?.smoothScrollTo(0, targetTop)
            bluetoothRoomPanel.requestFocus()
        }
    }

    private fun applyRuleSelection() {
        // 规则单选控件隐藏在布局里，仍作为默认规则和蓝牙同步规则的本地状态来源。
        selectedRuleSetType = if (::rbNorth.isInitialized && rbNorth.isChecked) {
            RuleSetType.NORTH
        } else {
            RuleSetType.SOUTH
        }
    }

    /** 当前规则枚举对应的展示名。 */
    private fun selectedRuleProfileName(): String {
        return RuleProfiles.from(selectedRuleSetType).displayName
    }

    /** 从本地选择或蓝牙消息应用规则，同时反写单选控件。 */
    private fun setSelectedRule(ruleSetType: RuleSetType) {
        selectedRuleSetType = ruleSetType
        if (::rbSouth.isInitialized && ::rbNorth.isInitialized) {
            rbSouth.isChecked = ruleSetType == RuleSetType.SOUTH
            rbNorth.isChecked = ruleSetType == RuleSetType.NORTH
        }
        if (::tvSetupSummary.isInitialized) {
            updateLobbySetupUi()
        }
    }

    /** 大厅中选择的真人总数，包含房主自己。 */
    private fun selectedHumanCount(): Int {
        return when {
            rbHumans4.isChecked -> 4
            rbHumans3.isChecked -> 3
            else -> 2
        }
    }

    /** 按大厅配置创建蓝牙房间，并把远端真人座位标记出来。 */
    private fun startConfiguredBluetoothRoom() {
        rbModeBluetooth.isChecked = true
        updateLobbySetupUi()
        val remoteHumanCount = (selectedHumanCount() - 1).coerceIn(1, PLAYER_IDS.size - 1)
        val humanSeats = PLAYER_IDS.drop(1).take(remoteHumanCount).toSet()
        hostBluetoothRoom(humanSeats)
    }

    /** 打开加入房间流程，滚动到蓝牙设置区并尝试连接。 */
    private fun openBluetoothJoinFlow() {
        playUiSound(selectSoundId)
        rbModeBluetooth.isChecked = true
        updateLobbySetupUi()
        scrollToBluetoothSettingsPanel()
        joinBluetoothRoom()
    }

    /** 请求当前系统版本需要的蓝牙运行时权限。 */
    private fun requestBluetoothPermissions() {
        val permissions = BluetoothPermissionHelper.requestPermissions()
        if (permissions.isEmpty()) {
            tvBluetoothStatus.text = "当前系统不需要额外的蓝牙运行时权限，可以选择已配对设备。"
            return
        }
        bluetoothPermissionLauncher.launch(permissions)
    }

    /** 创建本地人机房间：本机 p1，其他座位由 AI 托管。 */
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
        addLog("房间 $currentRoomId：${selectedRuleProfileName()}，3 个 AI 托管，自动开局")
        startNewGame()
    }

    /** 房主创建蓝牙房间并等待指定真人座位加入。 */
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
        addLog("蓝牙：创建房间 $currentRoomId，${selectedRuleProfileName()}，等待 ${humanSeats.size} 位真人加入")
        renderRoomState()
        renderTablePage()
    }

    /** 客户端按输入框中的主机 MAC 或设备名加入房间。 */
    private fun joinBluetoothRoom() {
        if (!BluetoothPermissionHelper.hasRequiredPermissions(this)) {
            requestBluetoothPermissions()
            return
        }

        rbModeBluetooth.isChecked = true
        updateLobbySetupUi()
        currentRoomId = roomIdFromInput(defaultValue = "")
        if (currentRoomId.isEmpty()) {
            tvBluetoothStatus.text = "请点“已配对”选择主机手机，或输入主机 MAC / 已配对设备名。"
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

    /** 断开蓝牙并把联机相关状态恢复为本地模式。 */
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
        stopTurnCountdown()
        renderRoomState()
    }

    /** 从大厅切换到房间/牌桌页面。 */
    private fun enterRoomPage() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setTableImmersiveMode(enabled = true)
        setupPage.visibility = View.GONE
        roomPage.visibility = View.VISIBLE
        renderTablePage()
    }

    /** 离开牌桌并回到大厅，同时清理蓝牙和定时任务。 */
    private fun leaveRoomPage() {
        handler.removeCallbacksAndMessages(null)
        disconnectBluetoothRoom()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setTableImmersiveMode(enabled = true)
        roomPage.visibility = View.GONE
        setupPage.visibility = View.VISIBLE
        initializeLobbySetup()
    }

    // 统一控制大厅和牌桌的系统栏，页面切换后仍保持沉浸式横屏。
    private fun setTableImmersiveMode(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** 启动或重启联机心跳循环。 */
    private fun startHeartbeatLoop() {
        handler.removeCallbacks(heartbeatRunnable)
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    /** 当前座位标记准备；客户端会把 Ready 发给房主。 */
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

    /** 读取并展示系统已配对设备，便于客户端选择主机。 */
    private fun showBondedBluetoothDevices() {
        if (!BluetoothPermissionHelper.hasRequiredPermissions(this)) {
            requestBluetoothPermissions()
            return
        }

        val peers = ensureSyncManager(networkPlayerId).bondedPeers()
        renderBondedPeers(peers)
    }

    /** 确保蓝牙管理器与当前网络玩家 id 匹配，不匹配时重建连接管理器。 */
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

    /** 蓝牙连接状态回调：更新状态栏、日志和心跳基准时间。 */
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

    /** 渲染已配对设备列表，点击设备后填入连接地址。 */
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

    /**
     * 蓝牙业务消息总入口。
     *
     * 房主在这里分配座位、校验远端动作并广播快照；客户端在这里接收座位、快照和私人手牌。
     */
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
                    readyPlayers.add(localPlayerId)
                    lastHeartbeatByPlayer.clear()
                    syncManager?.sendMessage(BluetoothMessage.Ready(localPlayerId))
                    addLog("蓝牙：你被分配到座位 ${message.assignedPlayerId}")
                    tvBluetoothStatus.text = "已加入座位 ${message.assignedPlayerId}，等待房主开局。"
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
                if (bluetoothRole == BluetoothRole.CLIENT) {
                    setSelectedRule(message.ruleSetType)
                } else {
                    selectedRuleSetType = message.ruleSetType
                }
                roomPlayers.clear()
                roomPlayers.addAll(message.players)
                readyPlayers.clear()
                readyPlayers.addAll(message.readyPlayers)
                bluetoothHumanSeats.clear()
                bluetoothHumanSeats.addAll(message.bluetoothPlayers.filter { it in PLAYER_IDS && it != HUMAN_ID })
                tvBluetoothStatus.text = "房间规则：${selectedRuleProfileName()}，玩家：${message.players.joinToString("，")}"
                addLog("蓝牙房间玩家：${message.players.joinToString("，")}")
            }
            is BluetoothMessage.StartGame -> {
                setSelectedRule(message.ruleSetType)
                if (bluetoothRole != BluetoothRole.LOCAL && roomPage.visibility != View.VISIBLE) {
                    enterRoomPage()
                }
                addLog("蓝牙：收到${selectedRuleProfileName()}开局种子 ${message.seed}")
                if (bluetoothRole != BluetoothRole.HOST) {
                    startNewGame(
                        seed = message.seed,
                        broadcastStart = false,
                        ruleSetType = message.ruleSetType
                    )
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
                        syncManager?.sendMessage(
                            BluetoothMessage.StartGame(
                                controller.state.roundSeed,
                                controller.ruleProfile.type
                            )
                        )
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

    /** 房主在所有真人座位准备后自动开局。 */
    private fun startGameWhenRoomReady() {
        if (bluetoothRole == BluetoothRole.HOST && !roomGameStarted && allJoinedPlayersReady()) {
            addLog("蓝牙：真人座位已准备，自动开局")
            startNewGame()
        }
    }

    /** 处理远端出牌请求或房主确认消息。 */
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
            // 房主是权威状态源，必须先在本地控制器校验合法性，再广播确认和快照。
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

    /** 处理远端过牌请求或房主确认消息。 */
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

    /** 将房主广播的权威快照应用到本机局面。 */
    private fun applyBluetoothSnapshot(message: BluetoothMessage.GameStateSnapshot) {
        if (bluetoothRole != BluetoothRole.LOCAL && roomPage.visibility != View.VISIBLE) {
            enterRoomPage()
        }
        setSelectedRule(message.ruleSetType)
        reconcileClientSeatFromSnapshot(message)
        if (!::controller.isInitialized ||
            controller.state.roundSeed != message.seed ||
            controller.ruleProfile.type != message.ruleSetType
        ) {
            // 种子或规则变化说明进入了新局，需要先创建同规则同 seed 的控制器。
            startNewGame(
                seed = message.seed,
                broadcastStart = false,
                ruleSetType = message.ruleSetType
            )
        }

        applySnapshotRoomState(message)
        roomGameStarted = true
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
        controller.state.lastPlayPlayerId = message.lastPlayPlayerId
        controller.state.passCount = message.passCount
        controller.state.firstRound = message.firstRound
        controller.state.lastWinnerId = message.lastWinnerId
        visibleHandCounts.putAll(message.handCounts)
        applySnapshotPrivateHand(message)
        controller.state.finishOrder.clear()
        controller.state.finishOrder.addAll(message.finishOrder)
        lastWinnerId = message.lastWinnerId
        roundOver = controller.isRoundComplete()
        selectedCards.clear()
        if (bluetoothRole != BluetoothRole.CLIENT) {
            waitingForHost = false
        }
        if (roundOver) {
            stopTurnCountdown()
        } else {
            syncTurnCountdown(reset = false)
        }

        addLog(
            "蓝牙快照：当前 ${message.currentPlayerId}，手牌数 ${
                message.handCounts.entries.joinToString("，") { "${it.key}:${it.value}" }
            }"
        )
        render()
    }

    /** 客户端根据定向私人手牌快照校正自己的正式座位。 */
    private fun reconcileClientSeatFromSnapshot(message: BluetoothMessage.GameStateSnapshot) {
        if (bluetoothRole != BluetoothRole.CLIENT) return
        val privateSeat = message.hands.keys.singleOrNull { it in PLAYER_IDS && it != HUMAN_ID } ?: return
        if (localPlayerId == privateSeat) return

        localPlayerId = privateSeat
        networkPlayerId = privateSeat
        syncManagerOwnerId = privateSeat
        roomPlayers.add(HUMAN_ID)
        roomPlayers.add(privateSeat)
        readyPlayers.add(privateSeat)
        addLog("蓝牙：已从主机快照校正你的座位为 $privateSeat")
    }

    /** 从快照中同步房间玩家、准备状态和蓝牙真人座位。 */
    private fun applySnapshotRoomState(message: BluetoothMessage.GameStateSnapshot) {
        if (message.players.isNotEmpty()) {
            roomPlayers.clear()
            roomPlayers.addAll(message.players.filter { it in PLAYER_IDS })
        } else if (bluetoothRole == BluetoothRole.CLIENT) {
            roomPlayers.add(HUMAN_ID)
            roomPlayers.add(localPlayerId)
        }

        if (message.readyPlayers.isNotEmpty()) {
            readyPlayers.clear()
            readyPlayers.addAll(message.readyPlayers.filter { it in PLAYER_IDS })
        } else if (bluetoothRole == BluetoothRole.CLIENT) {
            readyPlayers.addAll(roomPlayers)
        }

        if (message.bluetoothPlayers.isNotEmpty()) {
            bluetoothHumanSeats.clear()
            bluetoothHumanSeats.addAll(message.bluetoothPlayers.filter { it in PLAYER_IDS && it != HUMAN_ID })
        }
    }

    /** 应用快照中只发给本机的私人手牌。 */
    private fun applySnapshotPrivateHand(message: BluetoothMessage.GameStateSnapshot) {
        val encodedHand = message.hands[localPlayerId] ?: return
        val cards = CardWireCodec.decodeList(encodedHand)
        if (cards == null) {
            addLog("蓝牙：快照里的私人手牌无法解析")
            return
        }

        humanPlayer().handCards.clear()
        humanPlayer().handCards.addAll(sortHand(cards))
        visibleHandCounts[localPlayerId] = cards.size
    }

    /** 应用独立 PrivateHand 消息中的本机手牌。 */
    private fun applyPrivateHand(message: BluetoothMessage.PrivateHand) {
        if (message.playerId != localPlayerId) return
        val cards = CardWireCodec.decodeList(message.cards)
        if (cards == null) {
            addLog("蓝牙：收到无法解析的私人手牌")
            return
        }

        humanPlayer().handCards.clear()
        humanPlayer().handCards.addAll(sortHand(cards))
        visibleHandCounts[localPlayerId] = cards.size
        selectedCards.clear()
        addLog("蓝牙：已更新你的私人手牌，共 ${cards.size} 张")
        render()
    }

    /** 创建一局新游戏；房主会同步 seed 和规则，客户端只本地重建控制器。 */
    private fun startNewGame(
        seed: Long = System.currentTimeMillis(),
        broadcastStart: Boolean = true,
        ruleSetType: RuleSetType = selectedRuleSetType
    ) {
        if (broadcastStart && bluetoothRole == BluetoothRole.HOST && !allJoinedPlayersReady()) {
            val waiting = waitingHumanSeats()
            tvBluetoothStatus.text = "还有玩家未准备：${waiting.joinToString("，")}"
            addLog("蓝牙：等待玩家准备 ${waiting.joinToString("，")}")
            renderLog()
            return
        }

        if (bluetoothRole == BluetoothRole.CLIENT) {
            // 客户端保留心跳，不清空所有 Handler 任务，避免断开联机保活。
            handler.removeCallbacks(heartbeatRunnable)
        } else {
            handler.removeCallbacksAndMessages(null)
        }
        selectedCards.clear()
        roundOver = false
        roomGameStarted = true
        roundNumber += 1
        setSelectedRule(ruleSetType)
        stopTurnCountdown()

        val players = createPlayers()
        val config = GameConfig(scoringMode = ScoringMode.SCORE, ruleSetType = ruleSetType)
        controller = GameController(config, players)
        controller.state.lastWinnerId = lastWinnerId
        controller.startGame(seed)
        visibleHandCounts.clear()
        controller.state.players.forEach { player ->
            visibleHandCounts[player.id] = player.handCards.size
        }
        lastTablePromptText = null

        addLog("第 ${roundNumber} 局：${controller.ruleProfile.displayName}开局")
        addLog("先手：${currentPlayer().name}")
        if (broadcastStart && bluetoothRole == BluetoothRole.HOST) {
            syncManager?.sendMessage(BluetoothMessage.StartGame(seed, controller.ruleProfile.type))
            sendBluetoothSnapshot()
        }
        if (bluetoothRole != BluetoothRole.LOCAL) {
            startHeartbeatLoop()
        }
        setGameMessage(
            if (currentPlayer().id == localPlayerId) {
                "请选择手牌出牌。"
            } else {
                "等待其他玩家。"
            }
        )
        syncTurnCountdown(reset = true)
        render()
        runAiTurns()
    }

    /** 重置整场比赛积分和局数；在大厅时则重置大厅配置。 */
    private fun resetMatch() {
        if (setupPage.visibility == View.VISIBLE && roomPage.visibility != View.VISIBLE) {
            resetLobbySetup()
            return
        }

        handler.removeCallbacksAndMessages(null)
        selectedCards.clear()
        logLines.clear()
        matchScores.clear()
        lastWinnerId = null
        roundNumber = 0
        roundOver = false
        roomGameStarted = false
        stopTurnCountdown()
        if (bluetoothRole != BluetoothRole.LOCAL) {
            startHeartbeatLoop()
        }
        startNewGame()
    }

    /** 本地玩家点击出牌后的统一处理。 */
    private fun playSelectedCards() {
        if (roundOver || currentPlayer().id != localPlayerId) return
        if (selectedCards.isEmpty()) {
            playUiSound(errorSoundId)
            setGameMessage("先点选你要出的牌。")
            return
        }

        val cards = selectedCards.toList().sorted()
        val play = HandEvaluator.evaluate(cards, controller.ruleProfile)
        if (play == null) {
            playUiSound(errorSoundId)
            setGameMessage("这组牌不是合法牌型。")
            return
        }

        if (!RuleEngine.canPlay(controller.state, humanPlayer().handCards, cards, controller.ruleProfile)) {
            playUiSound(errorSoundId)
            setGameMessage(invalidPlayMessage(cards))
            return
        }

        if (bluetoothRole == BluetoothRole.CLIENT) {
            // 客户端只发送请求，等待房主快照确认后再更新权威状态。
            syncManager?.sendMessage(
                BluetoothMessage.PlayCards(localPlayerId, CardWireCodec.encodeList(cards))
            )
            waitingForHost = true
            playUiSound(playSoundId)
            setGameMessage("已发送出牌请求，等待主机确认。")
            selectedCards.clear()
            render()
            return
        }

        if (!controller.playCards(localPlayerId, cards)) {
            playUiSound(errorSoundId)
            setGameMessage(invalidPlayMessage(cards))
            return
        }

        playUiSound(playSoundId)
        addLog("你 出牌 ${typeName(play.type)}：${cardsLabel(cards)}")
        syncManager?.sendMessage(BluetoothMessage.PlayCards(localPlayerId, CardWireCodec.encodeList(cards)))
        sendBluetoothSnapshot()
        selectedCards.clear()
        afterAction()
    }

    /** 本地玩家点击过牌后的统一处理。 */
    private fun passTurn() {
        if (roundOver || currentPlayer().id != localPlayerId) return
        if (!RuleEngine.canPass(controller.state)) {
            playUiSound(errorSoundId)
            setGameMessage("当前不能过牌，桌面没有上一手时必须出牌。")
            return
        }

        if (bluetoothRole == BluetoothRole.CLIENT) {
            syncManager?.sendMessage(BluetoothMessage.Pass(localPlayerId))
            waitingForHost = true
            playUiSound(passSoundId)
            setGameMessage("已发送过牌请求，等待主机确认。")
            selectedCards.clear()
            render()
            return
        }

        if (!controller.pass(localPlayerId)) {
            playUiSound(errorSoundId)
            setGameMessage("当前不能过牌，桌面没有上一手时必须出牌。")
            return
        }

        playUiSound(passSoundId)
        addLog("你 过牌")
        syncManager?.sendMessage(BluetoothMessage.Pass(localPlayerId))
        sendBluetoothSnapshot()
        selectedCards.clear()
        afterAction()
    }

    /** 选择当前最小合法候选牌，作为玩家提示。 */
    private fun selectHintCards() {
        if (roundOver || currentPlayer().id != localPlayerId) return

        val human = humanPlayer()
        val candidate = PlayCandidateFinder
            .findValidCandidates(controller.state, human.handCards, controller.ruleProfile)
            .firstOrNull()

        selectedCards.clear()
        if (candidate == null) {
            playUiSound(errorSoundId)
            // 先刷新按钮/手牌状态，再显示结果，避免 render() 把提示覆盖回“轮到你出牌”。
            render()
            setGameMessage("你没有牌能比得上")
            return
        }

        playUiSound(selectSoundId)
        selectedCards.addAll(candidate)
        setGameMessage("已选中建议出牌：${cardsLabel(candidate)}", fade = true)
        render()
    }

    /** 任一动作完成后的收尾：结算、刷新、切倒计时并继续 AI。 */
    private fun afterAction() {
        if (controller.isRoundComplete()) {
            finishRound()
            return
        }

        val message = if (currentPlayer().id == localPlayerId) "轮到你了。" else "等待其他玩家..."
        setGameMessage(message, fade = message == "轮到你了。")
        syncTurnCountdown(reset = true)
        render()
        runAiTurns()
    }

    /** 当前轮到 AI 或托管座位时，延迟执行 AI 行动。 */
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

    /** 本局结束后的结算、统计持久化和联机广播。 */
    private fun finishRound() {
        roundOver = true
        selectedCards.clear()
        stopTurnCountdown()

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
        setGameMessage(roundResultMessage(winner?.name, scoreMap))
        // 本地只记录当前设备玩家的统计，联机对手不会写入本机偏好。
        try {
            UserPrefs.instance().incrementTotalGames()
            val myScoreDelta = scoreMap[localPlayerId] ?: 0
            UserPrefs.instance().addScoreRecord(myScoreDelta)
        } catch (e: Exception) {
            // 偏好未初始化不影响对局结算，保持游戏主流程可继续。
        }
        render()
    }

    /** 刷新旧版调试区域和当前牌桌页面。 */
    private fun render() {
        if (!::controller.isInitialized) return

        updateVisibleHandCountsFromLocalState()
        val current = currentPlayer()
        tvStatus.text = tableStatusText(current)

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
        btnPlay.isEnabled = humanTurn && canPlaySelectedCards() && !waitingForHost
        btnPass.isEnabled = humanTurn && RuleEngine.canPass(controller.state) && !waitingForHost
        btnHint.isEnabled = humanTurn && !waitingForHost
    }

    /** 生成本局结算提示文案。 */
    private fun roundResultMessage(winnerName: String?, scoreMap: Map<String, Int>): String {
        val scoreText = PLAYER_IDS.mapNotNull { playerId ->
            scoreMap[playerId]?.let { delta ->
                val signedDelta = if (delta > 0) "+$delta" else delta.toString()
                "${playerName(playerId)} $signedDelta"
            }
        }.joinToString("，")
        return "本局结束：${winnerName ?: "未知玩家"} 获胜。积分变化：$scoreText。点击“新局”继续。"
    }

    // 房间页每次根据当前游戏状态重绘，避免旧状态残留到新 UI 上。
    private fun renderTablePage() {
        if (!::roomPage.isInitialized || roomPage.visibility != View.VISIBLE) return

        tvTableStatus.text = tableTopStatusText()
        val canStartTableGame = bluetoothRole != BluetoothRole.CLIENT
        btnTableNewGame.isEnabled = canStartTableGame
        btnTableNewGame.alpha = if (canStartTableGame) 1f else 0.45f

        if (!roomGameStarted || !::controller.isInitialized) {
            val waitingText = waitingHumanSeats().takeIf { it.isNotEmpty() }?.let {
                "等待：${it.joinToString("、")}"
            } ?: "座位已准备，可以开始"
            showTablePrompt(waitingText)
            shouldFadeNextTablePrompt = false
            tableCardContainer.removeAllViews()
            renderTableSeats()
            renderLocalPlayerHud()
            setTableActionButtons(false, false, false)
            updateTableCountdownBadge()
            return
        }

        val promptText = tablePromptText()
        showTablePrompt(
            promptText,
            force = selectedCards.isNotEmpty() || shouldFadeNextTablePrompt
        )
        shouldFadeNextTablePrompt = false
        renderTableSeats()
        renderLocalPlayerHud()
        renderTableHand()

        val humanTurn = !roundOver && currentPlayer().id == localPlayerId
        setTableActionButtons(
            playEnabled = humanTurn && canPlaySelectedCards() && !waitingForHost,
            passEnabled = humanTurn && RuleEngine.canPass(controller.state) && !waitingForHost,
            hintEnabled = humanTurn && !waitingForHost
        )
        updateTableCountdownBadge()
    }

    /** 旧版顶部状态栏文案，包含当前玩家、规则、局数和倒计时。 */
    private fun tableStatusText(current: PlayerState = currentPlayer()): String {
        return buildString {
            append("当前：${current.name}")
            append("    规则：${controller.ruleProfile.displayName}")
            append("    局数：${roundNumber}")
            append("    已过牌：${controller.state.passCount}")
            turnCountdownLabel()?.let { append("    $it") }
            if (bluetoothRole != BluetoothRole.LOCAL) {
                append("    你的座位：${localPlayerId}")
            }
        }
    }

    /** 新牌桌顶部房间号文案。 */
    private fun tableTopStatusText(): String {
        val roomLabel = currentRoomId.ifEmpty {
            if (bluetoothRole == BluetoothRole.LOCAL) "离线" else "待创建"
        }
        return "房间号：$roomLabel"
    }

    /** 渲染除本地玩家外的三个座位，并根据本机视角映射到左/上/右。 */
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
            // 以本地玩家为基准重新映射座位，确保左/上/右三处位置始终和当前视角一致。
            val slot = tableSeatSlot(player.id) ?: return@forEachIndexed
            val seatView = createTableSeatView(player, index, isCurrent, joined, needsBluetooth, slot)
            when (slot) {
                TableSeatSlot.LEFT -> tableLeftSeats.addView(
                    seatView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                TableSeatSlot.TOP -> tableTopSeats.addView(
                    seatView,
                    LinearLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.MATCH_PARENT)
                )
                TableSeatSlot.RIGHT -> tableRightSeats.addView(
                    seatView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
    }

    /** 将绝对座位 p1-p4 转换为当前本机视角下的相对牌桌位置。 */
    private fun tableSeatSlot(playerId: String): TableSeatSlot? {
        if (playerId == localPlayerId) return null
        val localIndex = PLAYER_IDS.indexOf(localPlayerId).takeIf { it >= 0 } ?: 0
        val playerIndex = PLAYER_IDS.indexOf(playerId)
        if (playerIndex < 0) return null
        return when ((playerIndex - localIndex + PLAYER_IDS.size) % PLAYER_IDS.size) {
            1 -> TableSeatSlot.LEFT
            2 -> TableSeatSlot.TOP
            3 -> TableSeatSlot.RIGHT
            else -> null
        }
    }

    /** 构造单个远端/AI 座位视图。 */
    private fun createTableSeatView(
        player: PlayerState,
        index: Int,
        isCurrent: Boolean,
        joined: Boolean,
        needsBluetooth: Boolean,
        slot: TableSeatSlot
    ): LinearLayout {
        val handCount = visibleHandCount(player)
        val singleCardWarning = isSingleCardPlayer(player)
        val handText = if (roomGameStarted && ::controller.isInitialized) {
            "$handCount 张"
        } else {
            "待开局"
        }
        val name = displayNameForSeat(player.id, index)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            clipToPadding = false
            setPadding(0, 0, 0, 0)
            elevation = if (isCurrent) dp(8).toFloat() else dp(5).toFloat()
            val seatInfo = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                addView(
                    FrameLayout(this@MainActivity).apply {
                        addView(
                            ImageView(this@MainActivity).apply {
                                setImageResource(avatarResourceForSeat(player.id))
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            },
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        addView(
                            TextView(this@MainActivity).apply {
                                text = if (isCurrent) "$name 出牌" else name
                                gravity = android.view.Gravity.CENTER
                                includeFontPadding = false
                                textSize = 10.5f
                                typeface = Typeface.DEFAULT_BOLD
                                setTextColor(Color.BLACK)
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            },
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(30),
                                android.view.Gravity.BOTTOM
                            ).apply {
                                leftMargin = dp(4)
                                rightMargin = dp(4)
                            }
                        )
                        if (singleCardWarning) {
                            addView(
                                TextView(this@MainActivity).apply {
                                    text = "报单"
                                    gravity = android.view.Gravity.CENTER
                                    includeFontPadding = false
                                    textSize = 9.5f
                                    typeface = Typeface.DEFAULT_BOLD
                                    setTextColor(Color.parseColor("#4E2608"))
                                    background = roundedBackground("#FFFFD75B", "#FFFFF2A8", radiusDp = 12)
                                },
                                FrameLayout.LayoutParams(
                                    dp(42),
                                    dp(22),
                                    android.view.Gravity.TOP or android.view.Gravity.END
                                ).apply {
                                    topMargin = dp(3)
                                    rightMargin = dp(3)
                                }
                            )
                        }
                    },
                    LinearLayout.LayoutParams(dp(92), dp(108))
                )
                addView(
                    TextView(this@MainActivity).apply {
                        text = buildString {
                            append("$handText · ${seatStateLabel(player.id, joined, needsBluetooth)} · 分 ${player.score}")
                            if (singleCardWarning) append(" · 报单")
                        }
                        gravity = android.view.Gravity.CENTER
                        includeFontPadding = false
                        textSize = 8.2f
                        setTextColor(Color.parseColor(if (singleCardWarning) "#FFFFD75B" else "#E7FFF4D6"))
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(2)
                    }
                )
            }
            // 上家出的牌直接贴在头像旁边，避免只看中间牌堆时分不清是谁刚出的。
            val lastPlayPreview = createLastPlayPreview(player.id)
            if (slot == TableSeatSlot.RIGHT && lastPlayPreview != null) {
                addView(
                    lastPlayPreview,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dp(8)
                    }
                )
            }
            addView(
                seatInfo,
                LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            if (slot != TableSeatSlot.RIGHT && lastPlayPreview != null) {
                addView(
                    lastPlayPreview,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(8)
                    }
                )
            }
        }
    }

    /** 渲染本地玩家头像、分数和手牌数量。 */
    private fun renderLocalPlayerHud() {
        if (!::tableLocalHud.isInitialized) return
        tableLocalHud.removeAllViews()
        val player = if (roomGameStarted && ::controller.isInitialized) {
            humanPlayer()
        } else {
            createPlayers().first { it.id == localPlayerId }
        }
        val isCurrent = roomGameStarted && ::controller.isInitialized && player.id == currentPlayer().id && !roundOver
        val singleCardWarning = isSingleCardPlayer(player)
        tableLocalHud.addView(
            FrameLayout(this).apply {
                addView(
                    ImageView(this@MainActivity).apply {
                        setImageResource(avatarResourceForSeat(player.id))
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                addView(
                    TextView(this@MainActivity).apply {
                        text = if (isCurrent) "轮到你" else "你"
                        gravity = android.view.Gravity.CENTER
                        includeFontPadding = false
                        textSize = 10.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.BLACK)
                        maxLines = 1
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(30),
                        android.view.Gravity.BOTTOM
                    ).apply {
                        leftMargin = dp(4)
                        rightMargin = dp(4)
                    }
                )
                if (singleCardWarning) {
                    addView(
                        TextView(this@MainActivity).apply {
                            text = "报单"
                            gravity = android.view.Gravity.CENTER
                            includeFontPadding = false
                            textSize = 9.5f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.parseColor("#4E2608"))
                            background = roundedBackground("#FFFFD75B", "#FFFFF2A8", radiusDp = 12)
                        },
                        FrameLayout.LayoutParams(
                            dp(42),
                            dp(22),
                            android.view.Gravity.TOP or android.view.Gravity.END
                        ).apply {
                            topMargin = dp(3)
                            rightMargin = dp(3)
                        }
                    )
                }
            },
            LinearLayout.LayoutParams(dp(92), dp(108))
        )
        addScorePill(tableLocalHud, "分 ${player.score}")
        tableLocalHud.addView(
            TextView(this).apply {
                val handCount = visibleHandCount(player)
                text = if (roomGameStarted && ::controller.isInitialized) {
                    buildString {
                        append("手牌 $handCount")
                        if (singleCardWarning) append(" · 报单")
                    }
                } else {
                    "待开局"
                }
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
                textSize = 8.8f
                setTextColor(Color.parseColor(if (singleCardWarning) "#FFFFD75B" else "#E7FFF4D6"))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(1)
            }
        )
        renderLocalLastPlayPreview(player.id)
    }

    /** 本地玩家刚出过牌时，在自己面前显示上一手预览。 */
    private fun renderLocalLastPlayPreview(playerId: String) {
        if (!::tableLocalLastPlay.isInitialized) return
        tableLocalLastPlay.removeAllViews()

        // 本地玩家刚出的牌要显示在自己面前，而不是塞进左下角头像信息里。
        val preview = createLastPlayPreview(playerId)
        if (preview == null) {
            tableLocalLastPlay.visibility = View.GONE
            return
        }

        tableLocalLastPlay.visibility = View.VISIBLE
        tableLocalLastPlay.addView(
            preview,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    /** 为指定玩家创建“刚出”牌面预览；不是上一手出牌者时返回 null。 */
    private fun createLastPlayPreview(playerId: String): LinearLayout? {
        val cards = lastPlayCardsFor(playerId)
        if (cards.isEmpty()) return null

        // 所有“刚出”牌都用同一套尺寸，减少头像旁和本地玩家前方的视觉差异。
        val cardWidth = 32
        val cardHeight = 46
        val textSize = 10.5f
        val overlap = 8
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            clipToPadding = false
            background = roundedBackground("#99052319", "#CCFFD75B", radiusDp = 8)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            addView(
                TextView(this@MainActivity).apply {
                    text = "刚出"
                    gravity = android.view.Gravity.CENTER
                    includeFontPadding = false
                    this.textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#FFFFF5D0"))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    orientation = LinearLayout.HORIZONTAL
                    clipToPadding = false
                    cards.forEachIndexed { cardIndex, card ->
                        addView(
                            miniCardView(card, textSize),
                            LinearLayout.LayoutParams(dp(cardWidth), dp(cardHeight)).apply {
                                if (cardIndex > 0) marginStart = -dp(overlap)
                            }
                        )
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(3)
                }
            )
        }
    }

    /** 获取指定玩家刚出的牌，只展示当前桌面上一手。 */
    private fun lastPlayCardsFor(playerId: String): List<Card> {
        if (!::controller.isInitialized || !roomGameStarted) return emptyList()
        val lastPlay = controller.state.lastPlay ?: return emptyList()
        return if (controller.state.lastPlayPlayerId == playerId) {
            lastPlay.cards.sorted()
        } else {
            emptyList()
        }
    }

    /** 小号牌面视图，用于头像旁的出牌预览。 */
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

    /** 在玩家 HUD 中添加金币风格分数标签。 */
    private fun addScorePill(parent: LinearLayout, text: String) {
        parent.addView(
            TextView(this).apply {
                this.text = text
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#4E2608"))
                background = resources.getDrawable(R.drawable.coin_pill, theme)
                setPadding(dp(6), 0, dp(6), 0)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(17)
            ).apply {
                topMargin = dp(2)
            }
        )
    }

    /** 渲染座位背面牌预览，最多展示四张重叠牌背。 */
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
                        LinearLayout.LayoutParams(dp(22), dp(27)).apply {
                            if (previewIndex > 0) marginStart = -dp(8)
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

    /** 根据座位选择头像资源。 */
    private fun avatarResourceForSeat(playerId: String): Int {
        return when (playerId) {
            HUMAN_ID -> R.drawable.avatar_player
            "p2" -> R.drawable.avatar_ai_2
            "p3" -> R.drawable.avatar_ai_3
            "p4" -> R.drawable.avatar_ai_4
            else -> R.drawable.avatar_ai_2
        }
    }

    /** 生成座位状态标签：你、真人、等待真人或 AI。 */
    private fun seatStateLabel(playerId: String, joined: Boolean, needsBluetooth: Boolean): String {
        return when {
            playerId == localPlayerId -> "你"
            joined -> "真人"
            needsBluetooth -> "等待真人"
            else -> "AI"
        }
    }

    /** 获取可展示的手牌数量；客户端优先使用公共快照中的数量。 */
    private fun visibleHandCount(player: PlayerState): Int {
        return visibleHandCounts[player.id] ?: player.handCards.size
    }

    /** 判断玩家是否处于报单状态。 */
    private fun isSingleCardPlayer(player: PlayerState): Boolean {
        if (!roomGameStarted || !::controller.isInitialized || roundOver) return false
        if (player.id in controller.state.finishOrder) return false
        return visibleHandCount(player) == 1
    }

    /** 汇总所有报单玩家，生成牌桌提示文本。 */
    private fun singleCardWarningText(): String? {
        if (!roomGameStarted || !::controller.isInitialized || roundOver) return null
        val names = controller.state.players
            .filter(::isSingleCardPlayer)
            .map { player -> if (player.id == localPlayerId) "你" else player.name }

        return names.takeIf { it.isNotEmpty() }?.joinToString(
            prefix = "报单提醒：",
            separator = "、",
            postfix = " 剩 1 张"
        )
    }

    /** 渲染牌桌底部本地玩家手牌。 */
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
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
                minWidth = dp(TABLE_CARD_WIDTH_DP)
                minHeight = dp(86)
                setTextColor(cardTextColor(card.suit))
                setPadding(dp(3), dp(3), dp(3), dp(3))
                isEnabled = humanTurn
                alpha = if (humanTurn) 1f else 0.64f
                elevation = if (selected) dp(10).toFloat() else dp(4).toFloat()
                translationY = if (selected) -dp(12).toFloat() else 0f
                background = if (selected) {
                    resources.getDrawable(R.drawable.card_face_table_selected, theme)
                } else {
                    resources.getDrawable(R.drawable.card_face_table, theme)
                }
                setOnClickListener {
                    playUiSound(selectSoundId)
                    toggleSelectedCard(card)
                    render()
                }
            }
            val params = LinearLayout.LayoutParams(dp(TABLE_CARD_WIDTH_DP), dp(98)).apply {
                marginEnd = compactCardSpacing(cards.size, TABLE_CARD_WIDTH_DP, TABLE_RESERVED_WIDTH_DP)
            }
            tableCardContainer.addView(button, params)
        }
    }

    /** 统一设置牌桌操作按钮可用状态和透明度。 */
    private fun setTableActionButtons(playEnabled: Boolean, passEnabled: Boolean, hintEnabled: Boolean) {
        btnTablePlay.isEnabled = playEnabled
        btnTablePass.isEnabled = passEnabled
        btnTableHint.isEnabled = hintEnabled
        btnTablePlay.alpha = if (playEnabled) 1f else 0.72f
        btnTablePass.alpha = if (passEnabled) 1f else 0.52f
        btnTableHint.alpha = if (hintEnabled) 1f else 0.52f
    }

    /** 渲染旧版玩家列表，用于调试和兼容紧凑布局。 */
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

    /** 房主广播房间座位、准备状态和规则选择。 */
    private fun broadcastRoomState() {
        if (bluetoothRole == BluetoothRole.LOCAL) return
        syncManager?.sendMessage(
            BluetoothMessage.RoomState(
                players = roomPlayers.toList(),
                readyPlayers = readyPlayers.filter { it in roomPlayers },
                bluetoothPlayers = bluetoothHumanSeats.toList(),
                ruleSetType = selectedRuleSetType
            )
        )
        renderRoomState()
    }

    /** 房主在未加入座位上切换“蓝牙真人/AI 托管”。 */
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

    /** 渲染蓝牙房间座位状态列表。 */
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

    /** 渲染旧版底部手牌按钮。 */
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
                alpha = if (humanTurn) 1f else 0.68f
                elevation = if (selected) dp(7).toFloat() else dp(2).toFloat()
                translationY = if (selected) -dp(8).toFloat() else 0f
                background = roundedBackground(
                    fillColor = if (selected) "#FFE4A6" else "#FFFDF2",
                    strokeColor = if (selected) "#F1C45B" else "#D3A663",
                    radiusDp = 8
                )
                setOnClickListener {
                    playUiSound(selectSoundId)
                    toggleSelectedCard(card)
                    render()
                }
            }
            val params = LinearLayout.LayoutParams(dp(COMPACT_CARD_WIDTH_DP), dp(88)).apply {
                marginEnd = compactCardSpacing(cards.size, COMPACT_CARD_WIDTH_DP, SETUP_RESERVED_WIDTH_DP)
            }
            cardContainer.addView(button, params)
        }
    }

    /** 渲染当前选牌说明。 */
    private fun renderSelection() {
        showMessage(tvSelection, selectionDescription(), fade = selectedCards.isNotEmpty())
    }

    /** 切换单张牌的选中状态。 */
    private fun toggleSelectedCard(card: Card) {
        if (card in selectedCards) {
            selectedCards.remove(card)
        } else {
            selectedCards.add(card)
        }
    }

    /** 根据当前选牌生成牌型、张数和可出状态说明。 */
    private fun selectionDescription(): String {
        if (selectedCards.isEmpty()) return "未选择手牌"

        val cards = selectedCards.toList().sorted()
        val play = HandEvaluator.evaluate(cards, controller.ruleProfile)
        val type = play?.type?.let { typeName(it) } ?: "非法牌型"
        val playState = if (canPlaySelectedCards()) "可出" else "不可出"
        return "已选：$type · ${cards.size} 张 · $playState  ${cardsLabel(cards)}"
    }

    /** 牌桌中央提示文案，优先展示选牌状态和报单提醒。 */
    private fun tablePromptText(): String {
        if (selectedCards.isNotEmpty()) return selectionDescription()
        if (roundOver || waitingForHost) return tvMessage.text.toString()

        val current = currentPlayer()
        val baseText = if (current.id == localPlayerId) {
            "轮到你出牌"
        } else {
            "等待 ${current.name} 出牌"
        }
        val warningText = singleCardWarningText()
        return if (warningText == null) baseText else "$baseText · $warningText"
    }

    /** 判断当前选择的牌是否能在当前局面中打出。 */
    private fun canPlaySelectedCards(): Boolean {
        if (selectedCards.isEmpty()) return false
        return RuleEngine.canPlay(
            controller.state,
            humanPlayer().handCards,
            selectedCards.toList().sorted(),
            controller.ruleProfile
        )
    }

    // 倒计时跟随当前出牌玩家；只有换人或强制重置时才刷新截止时间。
    private fun syncTurnCountdown(reset: Boolean) {
        if (!::controller.isInitialized || !roomGameStarted || roundOver) {
            stopTurnCountdown()
            return
        }

        val currentId = currentPlayer().id
        if (reset || turnTimerPlayerId != currentId) {
            turnTimerPlayerId = currentId
            turnTimerDeadlineAt = System.currentTimeMillis() + TURN_TIME_LIMIT_MS
        }

        handler.removeCallbacks(turnCountdownRunnable)
        handler.post(turnCountdownRunnable)
        updateTurnCountdownUi()
    }

    /** 停止回合倒计时并隐藏牌桌倒计时徽标。 */
    private fun stopTurnCountdown() {
        handler.removeCallbacks(turnCountdownRunnable)
        turnTimerPlayerId = null
        turnTimerDeadlineAt = 0L
        updateTableCountdownBadge()
    }

    /** 刷新倒计时相关 UI，不改变截止时间。 */
    private fun updateTurnCountdownUi() {
        if (!::controller.isInitialized || !::tvStatus.isInitialized) return
        tvStatus.text = tableStatusText()
        if (::roomPage.isInitialized && roomPage.visibility == View.VISIBLE) {
            tvTableStatus.text = tableTopStatusText()
            updateTableCountdownBadge()
        }
    }

    // 牌桌上的倒计时徽标只负责显示，真正的计时和超时处理仍由 turnCountdownRunnable 管理。
    private fun updateTableCountdownBadge() {
        if (!::tvTableCountdown.isInitialized) return

        val shouldShow = ::controller.isInitialized &&
            roomGameStarted &&
            !roundOver &&
            turnTimerPlayerId == currentPlayer().id &&
            turnTimerDeadlineAt > 0L

        if (!shouldShow) {
            tvTableCountdown.visibility = View.GONE
            return
        }

        val remainingSeconds = turnCountdownRemainingSeconds()
        tvTableCountdown.visibility = View.VISIBLE
        tvTableCountdown.text = "倒计时\n${remainingSeconds}s"
        tvTableCountdown.setTextColor(
            Color.parseColor(if (remainingSeconds <= 5) "#FFFF5A4F" else "#FFFFF5D0")
        )
    }

    /** 旧版状态栏中展示的倒计时短文本。 */
    private fun turnCountdownLabel(): String? {
        if (!::controller.isInitialized || !roomGameStarted || roundOver) return null
        if (turnTimerPlayerId != currentPlayer().id || turnTimerDeadlineAt <= 0L) return null
        return "倒计时 ${turnCountdownRemainingSeconds()}s"
    }

    /** 计算剩余秒数，向上取整避免刚进入下一秒就显示 0。 */
    private fun turnCountdownRemainingSeconds(): Int {
        val remainingMs = (turnTimerDeadlineAt - System.currentTimeMillis()).coerceAtLeast(0L)
        return ((remainingMs + TURN_COUNTDOWN_TICK_MS - 1) / TURN_COUNTDOWN_TICK_MS).toInt()
    }

    /** 回合超时入口，根据是否允许过牌选择自动过牌或自动出牌。 */
    private fun handleTurnTimeout(playerId: String) {
        if (!::controller.isInitialized || roundOver || currentPlayer().id != playerId) return
        if (!shouldHandleTurnTimeout(playerId)) {
            updateTurnCountdownUi()
            return
        }

        stopTurnCountdown()
        selectedCards.clear()
        if (RuleEngine.canPass(controller.state)) {
            autoPassTimedOutPlayer(playerId)
        } else {
            autoPlayTimedOutPlayer(playerId)
        }
    }

    /** 判断当前设备是否有权处理这个玩家的超时。 */
    private fun shouldHandleTurnTimeout(playerId: String): Boolean {
        if (waitingForHost) return false
        return when (bluetoothRole) {
            BluetoothRole.LOCAL -> true
            BluetoothRole.HOST -> true
            BluetoothRole.CLIENT -> playerId == localPlayerId
        }
    }

    /** 超时后自动过牌；客户端只发请求，房主直接推进权威状态。 */
    private fun autoPassTimedOutPlayer(playerId: String) {
        if (bluetoothRole == BluetoothRole.CLIENT) {
            syncManager?.sendMessage(BluetoothMessage.Pass(playerId))
            waitingForHost = true
            playUiSound(passSoundId)
            addLog("${playerName(playerId)} 超时自动过牌")
            setGameMessage("倒计时结束，已自动过牌，等待主机确认。")
            render()
            return
        }

        if (!controller.pass(playerId)) {
            autoPlayTimedOutPlayer(playerId)
            return
        }

        playUiSound(passSoundId)
        addLog("${playerName(playerId)} 超时自动过牌")
        if (bluetoothRole == BluetoothRole.HOST) {
            syncManager?.sendMessage(BluetoothMessage.Pass(playerId))
            sendBluetoothSnapshot()
        }
        afterAction()
    }

    /** 超时且不能过牌时，选择最小合法牌自动出牌。 */
    private fun autoPlayTimedOutPlayer(playerId: String) {
        val player = controller.state.players.firstOrNull { it.id == playerId } ?: return
        val cards = PlayCandidateFinder
            .findValidCandidates(controller.state, player.handCards, controller.ruleProfile)
            .firstOrNull()

        if (cards == null) {
            addLog("${playerName(playerId)} 超时，但没有可自动出的合法牌")
            setGameMessage("${playerName(playerId)} 超时，但没有可自动出的合法牌。")
            render()
            return
        }

        val play = HandEvaluator.evaluate(cards, controller.ruleProfile)
        if (bluetoothRole == BluetoothRole.CLIENT) {
            syncManager?.sendMessage(
                BluetoothMessage.PlayCards(playerId, CardWireCodec.encodeList(cards))
            )
            waitingForHost = true
            playUiSound(playSoundId)
            addLog("${playerName(playerId)} 超时自动出牌 ${typeName(play?.type)}：${cardsLabel(cards)}")
            setGameMessage("倒计时结束，已自动出牌，等待主机确认。")
            render()
            return
        }

        if (!controller.playCards(playerId, cards)) {
            addLog("${playerName(playerId)} 超时自动出牌失败")
            render()
            return
        }

        playUiSound(playSoundId)
        addLog("${playerName(playerId)} 超时自动出牌 ${typeName(play?.type)}：${cardsLabel(cards)}")
        if (bluetoothRole == BluetoothRole.HOST) {
            syncManager?.sendMessage(
                BluetoothMessage.PlayCards(playerId, CardWireCodec.encodeList(cards))
            )
            sendBluetoothSnapshot()
        }
        afterAction()
    }

    /** 渲染最近对局日志。 */
    private fun renderLog() {
        tvLog.text = if (logLines.isEmpty()) {
            "对局日志会显示在这里。"
        } else {
            logLines.takeLast(10).joinToString("\n")
        }
    }

    /** 统一开关旧版出牌、过牌和提示按钮。 */
    private fun setActionButtonsEnabled(enabled: Boolean) {
        btnPlay.isEnabled = enabled
        btnPass.isEnabled = enabled
        btnHint.isEnabled = enabled
    }

    /** 给非法出牌生成更具体的提示。 */
    private fun invalidPlayMessage(cards: List<Card>): String {
        val state = controller.state
        return when {
            state.firstRound &&
                controller.ruleProfile.firstRoundMustContainDiamondThree &&
                Card.DIAMOND_THREE !in cards -> "${controller.ruleProfile.displayName}首轮必须包含方块 3。"
            state.lastPlay != null && state.lastPlay?.cards?.size != cards.size -> "需要出和上一手相同张数的牌。"
            else -> "这手牌压不过上一手，或者现在还不能这样出。"
        }
    }

    /** 当前轮到行动的玩家。 */
    private fun currentPlayer(): PlayerState {
        return controller.state.players[controller.state.currentPlayerIndex]
    }

    /** 本机对应的玩家状态。 */
    private fun humanPlayer(): PlayerState {
        return controller.state.players.first { it.id == localPlayerId }
    }

    /** 根据本机可见信息刷新手牌数量缓存。 */
    private fun updateVisibleHandCountsFromLocalState() {
        if (!::controller.isInitialized) return
        if (bluetoothRole == BluetoothRole.CLIENT) {
            controller.state.players.firstOrNull { it.id == localPlayerId }?.let { player ->
                visibleHandCounts[player.id] = player.handCards.size
            }
            return
        }

        controller.state.players.forEach { player ->
            visibleHandCounts[player.id] = player.handCards.size
        }
    }

    /** 为 AI 座位分配策略，p3 使用不同策略制造风格差异。 */
    private fun strategyFor(playerId: String): PlayStrategy {
        return if (playerId == "p3") ConservativeStrategy() else GreedyStrategy()
    }

    /** 判断当前回合是否应等待远端真人，而不是本机 AI 托管。 */
    private fun shouldWaitForRemotePlayer(): Boolean {
        val currentId = currentPlayer().id
        return when (bluetoothRole) {
            BluetoothRole.LOCAL -> false
            BluetoothRole.CLIENT -> currentId != localPlayerId
            BluetoothRole.HOST -> currentId != localPlayerId && currentId in roomPlayers
        }
    }

    /** 所有需要真人加入的座位是否已经加入并准备。 */
    private fun allJoinedPlayersReady(): Boolean {
        return requiredHumanSeats().all { playerId ->
            playerId in roomPlayers && playerId in readyPlayers
        }
    }

    /** 当前房间需要真人参与的座位列表。 */
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

    /** 尚未加入或尚未准备的真人座位。 */
    private fun waitingHumanSeats(): List<String> {
        return requiredHumanSeats().filter { playerId ->
            playerId !in roomPlayers || playerId !in readyPlayers
        }
    }

    /** 记录远端座位最近活动时间，用于心跳超时判断。 */
    private fun noteRemoteActivity(playerId: String, timestamp: Long = System.currentTimeMillis()) {
        if (playerId.isBlank() || playerId == localPlayerId) return
        lastHeartbeatByPlayer[playerId] = timestamp
        lastHeartbeatAt = timestamp
        heartbeatTimeoutReported = false
    }

    /** 检查心跳超时；房主按玩家检测，客户端按最后收到主机消息检测。 */
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
        tvBluetoothStatus.text = "蓝牙心跳超时，请检查连接后重新进入房间。"
        addLog("蓝牙：心跳超时，距离上次心跳 ${now - lastHeartbeatAt}ms")
        renderLog()
    }

    /** 标记玩家离线，并在房主端广播房间状态和最新快照。 */
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

    /** 将临时 guest id 映射为正式座位 id。 */
    private fun canonicalPlayerId(playerId: String): String {
        return clientSeatByRequestId[playerId] ?: playerId
    }

    /** 房主为加入请求分配一个空闲蓝牙真人座位。 */
    private fun assignSeat(requestPlayerId: String): String? {
        clientSeatByRequestId[requestPlayerId]?.let { return it }
        val usedSeats = roomPlayers + clientSeatByRequestId.values
        val seat = bluetoothHumanSeats.firstOrNull { it !in usedSeats } ?: return null
        clientSeatByRequestId[requestPlayerId] = seat
        return seat
    }

    /** 根据本机角色和座位状态生成玩家展示名。 */
    private fun displayNameForSeat(playerId: String, index: Int): String {
        if (playerId == localPlayerId) return "你"
        return when (bluetoothRole) {
            BluetoothRole.LOCAL -> "AI ${index + 1}"
            BluetoothRole.HOST -> if (playerId in roomPlayers || playerId in bluetoothHumanSeats) {
                "玩家 ${index + 1}"
            } else {
                "AI ${index + 1}"
            }
            BluetoothRole.CLIENT -> when {
                playerId == HUMAN_ID -> "主机"
                playerId in bluetoothHumanSeats -> "玩家 ${index + 1}"
                else -> "AI ${index + 1}"
            }
        }
    }

    /** 判断某个座位当前是否由本机 AI 托管。 */
    private fun isAiSeat(playerId: String): Boolean {
        return when (bluetoothRole) {
            BluetoothRole.LOCAL -> playerId != localPlayerId
            BluetoothRole.HOST -> playerId != localPlayerId &&
                playerId !in roomPlayers &&
                playerId !in bluetoothHumanSeats
            BluetoothRole.CLIENT -> false
        }
    }

    /** 按固定 p1-p4 座位创建玩家列表，并带入累计比分。 */
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

    /** 广播公共快照；房主会额外定向发送每个远端玩家的私人手牌。 */
    private fun sendBluetoothSnapshot() {
        if (!::controller.isInitialized) return
        syncManager?.sendMessage(createBluetoothSnapshot())
        if (bluetoothRole == BluetoothRole.HOST) {
            sendPrivateSnapshotsToRemotePlayers()
        }
    }

    /** 创建可广播或定向发送的权威局面快照。 */
    private fun createBluetoothSnapshot(
        privateHands: Map<String, List<String>> = emptyMap()
    ): BluetoothMessage.GameStateSnapshot {
        val state = controller.state
        return BluetoothMessage.GameStateSnapshot(
            seed = state.roundSeed,
            currentPlayerId = currentPlayer().id,
            lastPlayCards = state.lastPlay?.cards?.let(CardWireCodec::encodeList) ?: emptyList(),
            hands = privateHands,
            handCounts = state.players.associate { it.id to it.handCards.size },
            scores = state.players.associate { it.id to it.score },
            finishOrder = state.finishOrder.toList(),
            passCount = state.passCount,
            firstRound = state.firstRound,
            lastWinnerId = state.lastWinnerId,
            lastPlayPlayerId = state.lastPlayPlayerId,
            players = roomPlayers.toList(),
            readyPlayers = readyPlayers.filter { it in roomPlayers },
            bluetoothPlayers = bluetoothHumanSeats.toList(),
            ruleSetType = controller.ruleProfile.type
        )
    }

    /** 给每个远端真人发送只包含自己手牌的定向快照和 PrivateHand。 */
    private fun sendPrivateSnapshotsToRemotePlayers() {
        val manager = ensureSyncManager()
        controller.state.players.forEach { player ->
            if (player.id != localPlayerId && player.id in roomPlayers) {
                val encodedHand = CardWireCodec.encodeList(player.handCards)
                val sentSnapshot = manager.sendMessageToPlayer(
                    player.id,
                    createBluetoothSnapshot(privateHands = mapOf(player.id to encodedHand))
                )
                val sentHand = manager.sendMessageToPlayer(
                    player.id,
                    BluetoothMessage.PrivateHand(
                        playerId = player.id,
                        cards = encodedHand
                    )
                )
                if (!sentSnapshot && !sentHand) {
                    markPlayerOffline(
                        playerId = player.id,
                        reason = "私人手牌发送失败",
                        broadcast = true
                    )
                }
            }
        }
    }

    /** 从输入框读取房间/设备标识，空值时使用默认值。 */
    private fun roomIdFromInput(defaultValue: String): String {
        return etBluetoothRoom.text.toString().trim().ifEmpty { defaultValue }
    }

    /** 生成用于房主本地展示和服务名的六位房间号。 */
    private fun randomRoomId(): String {
        return (100000..999999).random().toString()
    }

    /** 统一手牌排序入口。 */
    private fun sortHand(cards: List<Card>): List<Card> {
        return cards.sorted()
    }

    /** 根据屏幕宽度计算手牌重叠间距，避免横屏小屏溢出。 */
    private fun compactCardSpacing(cardCount: Int, cardWidthDp: Int, reservedWidthDp: Int): Int {
        if (cardCount <= 1) return 0
        val cardWidth = dp(cardWidthDp)
        val availableWidth = (resources.displayMetrics.widthPixels - dp(reservedWidthDp)).coerceAtLeast(dp(280))
        val naturalWidth = cardWidth * cardCount
        if (naturalWidth <= availableWidth) return dp(4)

        val overlap = ((naturalWidth - availableWidth) / (cardCount - 1)).coerceAtMost(cardWidth - dp(24))
        return -overlap
    }

    /** 从控制器玩家列表中取展示名，找不到时回退 id。 */
    private fun playerName(playerId: String): String {
        return controller.state.players.firstOrNull { it.id == playerId }?.name ?: playerId
    }

    /** 添加日志并限制列表长度，避免长时间对局无限增长。 */
    private fun addLog(text: String) {
        logLines.add(text)
        if (logLines.size > 40) logLines.removeAt(0)
    }

    /** 一组牌的单行展示文本。 */
    private fun cardsLabel(cards: List<Card>): String {
        return cards.sorted().joinToString(" ") { "${suitSymbol(it.suit)}${rankLabel(it.rank)}" }
    }

    /** 牌按钮上的两行展示文本。 */
    private fun cardButtonLabel(card: Card): String {
        return "${suitSymbol(card.suit)}\n${rankLabel(card.rank)}"
    }

    /** 点数枚举到扑克牌面文字的映射。 */
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

    /** 花色枚举到符号的映射。 */
    private fun suitSymbol(suit: Suit): String {
        return when (suit) {
            Suit.DIAMOND -> "♦"
            Suit.CLUB -> "♣"
            Suit.HEART -> "♥"
            Suit.SPADE -> "♠"
        }
    }

    /** 红色花色和黑色花色的牌面文字颜色。 */
    private fun cardTextColor(suit: Suit): Int {
        return when (suit) {
            Suit.DIAMOND,
            Suit.HEART -> Color.parseColor("#B72A2A")
            Suit.CLUB,
            Suit.SPADE -> Color.parseColor("#1D1D1D")
        }
    }

    /** 牌型中文展示名。 */
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

    /** 蓝牙连接状态中文展示名。 */
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

    /** 快速创建纯色圆角背景，主要用于动态 TextView。 */
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

    /** dp 转 px。 */
    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        /** 固定四人座位 id，p1 始终代表房主/本地默认玩家。 */
        private const val HUMAN_ID = "p1"
        private const val COMPACT_CARD_WIDTH_DP = 48
        private const val TABLE_CARD_WIDTH_DP = 56
        private const val SETUP_RESERVED_WIDTH_DP = 32
        private const val TABLE_RESERVED_WIDTH_DP = 190
        private const val AI_TURN_DELAY_MS = 700L
        private const val TURN_TIME_LIMIT_MS = 20_000L
        private const val TURN_COUNTDOWN_TICK_MS = 1_000L
        private const val MESSAGE_FADE_IN_MS = 180L
        private const val MESSAGE_HOLD_MS = 1_250L
        private const val MESSAGE_FADE_OUT_MS = 520L
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val HEARTBEAT_TIMEOUT_MS = 15_000L
        private val PLAYER_IDS = listOf("p1", "p2", "p3", "p4")
    }

    /** 当前设备在对局中的联机身份。 */
    private enum class BluetoothRole {
        LOCAL,
        HOST,
        CLIENT
    }

    /** 远端座位相对本机玩家的牌桌位置。 */
    private enum class TableSeatSlot {
        LEFT,
        TOP,
        RIGHT
    }
}
