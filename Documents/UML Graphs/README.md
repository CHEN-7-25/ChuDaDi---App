# UML 图目录

本目录保存课程汇报用 UML 图片。图片反映项目设计意图，若和当前代码存在轻微差异，以 `Documents/当前框架说明.md` 和源代码为准。

## 文件说明

| 文件 | 用途 |
| --- | --- |
| `General System - Use Case Diagram.png` | 系统总用例图，描述本地对局、联机、南方规则、AI 和结算等主要能力。 |
| `Gameplay - Use Case Diagram.png` | 对局流程用例图，突出选牌、出牌、过牌、提示和结算。 |
| `Multiplayer - Use Case Diagram.png` | 蓝牙联机用例图，突出创建房间、加入房间、准备、同步和重连。 |
| `LogicView - Diagram.png` | 逻辑视图，描述 `model`、`rule`、`controller`、`ai`、`network`、`ui` 的模块关系。 |
| `状态机图.png` | 牌局状态流转图，适合讲解开局、轮转、过牌、结算和下一局。 |
| `用例图.png` | 早期综合用例图，保留作课程材料备份。 |

## 当前代码对齐说明

1. 当前 UI 主要集中在 `MainActivity`，并未拆分为多个 Activity 或 Fragment。
2. `GameController` 是核心牌局推进入口，规则判断由 `RuleEngine` 和 `HandEvaluator` 完成。
3. 蓝牙联机已有经典蓝牙 RFCOMM 连接、消息编解码、座位分配、准备、快照、私人手牌、心跳和手动重连。
4. 若后续重画 UML，建议把 `MainActivity` 中的房间协调逻辑单独画成可拆分的 `RoomCoordinator` 候选组件。
