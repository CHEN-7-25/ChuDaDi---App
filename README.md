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
- `network`：蓝牙联机消息协议与同步接口占位。
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
4. 首页点击“开始演示对局”可触发基础流程演示。

## 7. 测试说明
当前单元测试位于：
- `app/src/test/java/com/scut/chudadi/rule/HandEvaluatorTest.kt`
- `app/src/test/java/com/scut/chudadi/rule/RuleEngineTest.kt`
- `app/src/test/java/com/scut/chudadi/controller/GameControllerTest.kt`

可在 Android Studio 中运行 `test` 任务。

## 8. 文档入口
- 团队分工：[Documents/团队分工说明.md](Documents/团队分工说明.md)
- 开发补充：[Documents/南北规则与开发补充说明.md](Documents/南北规则与开发补充说明.md)
- 规则原文：[Documents/锄大地游戏规则设计.md](Documents/锄大地游戏规则设计.md)
