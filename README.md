# ChuDaDi---App

SCUT 24 级 软件工程 软件综合开发实训项目：锄大地多人对战安卓游戏（基础框架版）。

## 1. 当前完成情况
已根据 `uml安卓游戏设计.docx` 和《锄大地游戏规则设计》补齐以下内容：

1. 完整 Android Studio 工程骨架（`app` 模块 + Gradle Kotlin DSL）
2. 核心游戏框架（MVC 分层 + 模块化目录）
3. 南北规则切换（规则策略化）
4. AI 策略模式（贪心/保守两种）
5. 单元测试样例（牌型、规则、计分）
6. 团队分工与开发补充文档
7. 本地人机对战界面（1 名玩家 + 3 名 AI，支持选牌、出牌、过牌、提示、连续对局与累计比分）

## 2. 工程结构

```text
ChuDaDi---App/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/scut/chudadi/
│       │   │   ├── MainActivity.kt
│       │   │   ├── ai/
│       │   │   ├── controller/
│       │   │   ├── model/
│       │   │   ├── network/
│       │   │   ├── rule/
│       │   │   └── ui/
│       │   └── res/
│       └── test/java/com/scut/chudadi/
├── Documents/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 3. 模块说明
- `model`：卡牌/玩家/配置/状态对象。
- `rule`：牌型识别、规则比较、南北规则配置接口。
- `controller`：发牌、轮转、过牌、结算和回合状态推进。
- `ai`：策略模式接口与两种策略实现。
- `network`：蓝牙联机消息协议、消息编解码、主机/客户端连接管理。
- `ui`：ViewModel 层桥接（UI 调用控制器入口）。

## 4. 已实现规则点
- 首轮约束（南方规则要求必须带方块 3）。
- 牌型：单张、对子、三条、四炸、顺子、同花五、葫芦、铁支、同花顺。
- 五张牌比较优先级：同花顺 > 铁支 > 葫芦 > 同花五 > 顺子。
- 过牌与重开逻辑。
- 计分倍率：
1. `n < 8`：`n`
2. `8 <= n < 10`：`2n`
3. `10 <= n < 13`：`3n`
4. `n = 13`：`4n`

## 5. 南北规则切换（当前版本）
通过 `GameConfig.ruleSetType` 切换：
- `RuleSetType.SOUTH`
- `RuleSetType.NORTH`

当前实现差异定义如下：
1. 南方：首轮必须包含方块 3；同点数可比较花色；支持 `A2345` 顺子。
2. 北方：首轮不强制方块 3；同点数不比较花色；不支持 `A2345` 顺子。

说明：该差异用于“可扩展框架”落地，后续可根据你们最终课程规则再精确微调。

## 6. 运行方式
1. 用 Android Studio 打开项目根目录 `ChuDaDi---App`。
2. 等待 Gradle Sync 完成。
3. 运行 `app` 模块。
4. 进入首页后可直接开始一局本地人机对战；切换南方/北方规则后点击“下一局”生效。
5. 对局结束后点击“下一局”会保留累计比分；点击“重置比赛”会清空比分并重新开始。

## 7. 联机扩展说明
当前 `GameController.startGame(seed)` 已支持固定发牌种子。蓝牙联机时可由主机生成 seed 并广播给客户端，从而让多端复现同一副牌；也可以由主机直接广播完整 `GameState` 快照。

蓝牙模块位于 `app/src/main/java/com/scut/chudadi/network`：
1. `BluetoothMessage`：定义加入房间、准备、开局、出牌、过牌、状态快照、结算、掉线、心跳等消息。
2. `BluetoothMessageCodec`：将消息编码为按行传输的字符串，并负责解码。
3. `BluetoothGameSyncManager`：基于经典蓝牙 RFCOMM socket 实现主机/客户端连接、收发消息、断线通知。
4. `BluetoothPermissionHelper`：提供 Android 12 前后的蓝牙运行时权限列表。

当前连接约定：主机调用 `hostRoom(roomId)` 创建房间；客户端调用 `joinRoom(roomId)`，其中 `roomId` 可以传主机设备的蓝牙 MAC 地址，或已配对设备名称。主页已提供蓝牙调试区，可授权蓝牙、创建房间、加入房间和断开连接。

当前 UI 接入范围：
1. “已配对”按钮会列出系统已配对设备，点击后自动填入连接地址。
2. 房主创建房间后，可在房间状态面板把 `p2/p3/p4` 分别设为“蓝牙真人”或“AI 托管”。
3. 客户端加入房间后，主机会只从“蓝牙真人”座位里分配 `p2/p3/p4`，并广播 `SeatAssigned` / `RoomState`。
4. `RoomState` 会同步已加入玩家、已准备玩家和需要蓝牙连接的真人座位；主机点击“下一局”时只检查房主和蓝牙真人座位是否已加入并准备。
5. 客户端本地出牌/过牌只发送 `PlayCards` / `Pass` 请求，不直接推进状态。
6. 主机收到远端 `PlayCards` / `Pass` 后调用 `GameController` 校验，合法后广播动作和权威 `GameStateSnapshot`。
7. 主机通过 `PrivateHand` 定向发送客户端自己的手牌，公共快照不再广播全部手牌。
8. 客户端收到 `GameStateSnapshot` 后会应用当前玩家、上一手、分数、过牌数和结算状态并刷新界面。
9. 主页会显示房间座位状态（房主、等待蓝牙真人、蓝牙真人已加入、AI 托管、已准备/未准备）。
10. 联机状态下会定时发送带玩家座位号的 `Heartbeat`。主机检测到客户端超时后会标记该座位离线、广播房间状态，并用 AI 暂时代管该座位。
11. 客户端可点击“重连”重新建立连接并发送 `Reconnect`；主机收到后会恢复座位、重新广播房间状态、开局种子、公共快照和私人手牌。
12. 主机发送 `PrivateHand` 失败时会把对应座位标记离线，避免客户端没有手牌却继续停留在错误状态。

后续若要做更完整的四机实时对战，可继续补充：附近设备扫描、后台自动重连、房间状态抽离为独立协调器和更多真机稳定性测试记录。

## 8. 测试说明
当前单元测试位于：
- `app/src/test/java/com/scut/chudadi/rule/HandEvaluatorTest.kt`
- `app/src/test/java/com/scut/chudadi/rule/RuleEngineTest.kt`
- `app/src/test/java/com/scut/chudadi/controller/GameControllerTest.kt`
- `app/src/test/java/com/scut/chudadi/network/BluetoothMessageCodecTest.kt`
- `app/src/test/java/com/scut/chudadi/network/CardWireCodecTest.kt`

可在 Android Studio 中运行 `test` 任务。

## 9. 文档入口
- 团队分工：[Documents/团队分工说明.md](Documents/团队分工说明.md)
- 开发补充：[Documents/南北规则与开发补充说明.md](Documents/南北规则与开发补充说明.md)
- 规则原文：[Documents/锄大地游戏规则设计.md](Documents/锄大地游戏规则设计.md)
- 蓝牙测试：[Documents/蓝牙联机测试记录.md](Documents/蓝牙联机测试记录.md)
