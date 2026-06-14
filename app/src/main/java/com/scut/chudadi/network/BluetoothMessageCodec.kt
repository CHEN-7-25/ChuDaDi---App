package com.scut.chudadi.network

import com.scut.chudadi.model.RuleSetType
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 蓝牙业务消息的文本编解码器。
 *
 * 协议以首字段作为消息类型，后续字段用分隔符连接；每个字段再做 URL 编码，避免昵称或错误信息中的中文和符号破坏格式。
 */
object BluetoothMessageCodec {
    /** 将强类型消息编码成可直接按行发送的字符串。 */
    fun encode(message: BluetoothMessage): String {
        return when (message) {
            is BluetoothMessage.JoinRoom -> join("JOIN", message.playerId, message.playerName)
            is BluetoothMessage.SeatAssigned -> join(
                "SEAT",
                message.requestPlayerId,
                message.assignedPlayerId,
                encodeList(message.players)
            )
            is BluetoothMessage.Ready -> join("READY", message.playerId)
            is BluetoothMessage.RoomState -> join(
                "ROOM",
                encodeList(message.players),
                encodeList(message.readyPlayers),
                encodeList(message.bluetoothPlayers),
                message.ruleSetType.name
            )
            is BluetoothMessage.StartGame -> join(
                "START",
                message.seed.toString(),
                message.ruleSetType.name
            )
            is BluetoothMessage.PlayCards -> join(
                "PLAY",
                message.playerId,
                encodeList(message.cards)
            )
            is BluetoothMessage.Pass -> join("PASS", message.playerId)
            is BluetoothMessage.PrivateHand -> join(
                "HAND",
                message.playerId,
                encodeList(message.cards)
            )
            is BluetoothMessage.GameStateSnapshot -> join(
                "SNAPSHOT",
                message.seed.toString(),
                message.currentPlayerId,
                encodeList(message.lastPlayCards),
                encodeStringListMap(message.hands),
                encodeIntMap(message.handCounts),
                encodeIntMap(message.scores),
                encodeList(message.finishOrder),
                message.passCount.toString(),
                message.firstRound.toString(),
                message.lastWinnerId.orEmpty(),
                message.lastPlayPlayerId.orEmpty(),
                encodeList(message.players),
                encodeList(message.readyPlayers),
                encodeList(message.bluetoothPlayers),
                message.ruleSetType.name
            )
            is BluetoothMessage.RoundResult -> join("ROUND", encodeIntMap(message.scoreMap))
            is BluetoothMessage.PlayerOffline -> join("OFFLINE", message.playerId)
            is BluetoothMessage.Reconnect -> join("RECONNECT", message.playerId)
            is BluetoothMessage.Heartbeat -> join(
                "HEARTBEAT",
                message.timestamp.toString(),
                message.playerId
            )
            is BluetoothMessage.Error -> join("ERROR", message.reason)
        }
    }

    /** 将收到的一行文本解析成业务消息；格式错误时返回 null，交给上层提示错误。 */
    fun decode(line: String): BluetoothMessage? {
        val parts = line.split(FIELD_SEPARATOR)
        val type = parts.firstOrNull() ?: return null
        val fields = parts.drop(1).map(::decodeValue)

        // runCatching 保护字段缺失、数字格式错误等情况，避免读线程因为坏包崩溃。
        return runCatching {
            when (type) {
                "JOIN" -> BluetoothMessage.JoinRoom(fields[0], fields[1])
                "SEAT" -> BluetoothMessage.SeatAssigned(fields[0], fields[1], decodeList(fields[2]))
                "READY" -> BluetoothMessage.Ready(fields[0])
                "ROOM" -> BluetoothMessage.RoomState(
                    players = decodeList(fields[0]),
                    readyPlayers = fields.getOrNull(1)?.let(::decodeList).orEmpty(),
                    bluetoothPlayers = fields.getOrNull(2)?.let(::decodeList).orEmpty(),
                    ruleSetType = decodeRuleSetType(fields.getOrNull(3))
                )
                "START" -> BluetoothMessage.StartGame(
                    seed = fields[0].toLong(),
                    ruleSetType = decodeRuleSetType(fields.getOrNull(1))
                )
                "PLAY" -> BluetoothMessage.PlayCards(fields[0], decodeList(fields[1]))
                "PASS" -> BluetoothMessage.Pass(fields[0])
                "HAND" -> BluetoothMessage.PrivateHand(fields[0], decodeList(fields[1]))
                "SNAPSHOT" -> BluetoothMessage.GameStateSnapshot(
                    seed = fields[0].toLong(),
                    currentPlayerId = fields[1],
                    lastPlayCards = decodeList(fields[2]),
                    hands = decodeStringListMap(fields[3]),
                    handCounts = decodeIntMap(fields[4]),
                    scores = decodeIntMap(fields[5]),
                    finishOrder = decodeList(fields[6]),
                    passCount = fields[7].toInt(),
                    firstRound = fields[8].toBoolean(),
                    lastWinnerId = fields[9].ifEmpty { null },
                    lastPlayPlayerId = fields.getOrNull(10)?.ifEmpty { null },
                    players = fields.getOrNull(11)?.let(::decodeList).orEmpty(),
                    readyPlayers = fields.getOrNull(12)?.let(::decodeList).orEmpty(),
                    bluetoothPlayers = fields.getOrNull(13)?.let(::decodeList).orEmpty(),
                    ruleSetType = decodeRuleSetType(fields.getOrNull(14))
                )
                "ROUND" -> BluetoothMessage.RoundResult(decodeIntMap(fields[0]))
                "OFFLINE" -> BluetoothMessage.PlayerOffline(fields[0])
                "RECONNECT" -> BluetoothMessage.Reconnect(fields[0])
                "HEARTBEAT" -> BluetoothMessage.Heartbeat(
                    timestamp = fields[0].toLong(),
                    playerId = fields.getOrNull(1).orEmpty()
                )
                "ERROR" -> BluetoothMessage.Error(fields[0])
                else -> null
            }
        }.getOrNull()
    }

    /** 统一拼接消息类型和字段，并对字段做 URL 编码。 */
    private fun join(type: String, vararg fields: String): String {
        return buildList {
            add(type)
            fields.forEach { add(encodeValue(it)) }
        }.joinToString(FIELD_SEPARATOR)
    }

    /** 编码普通字符串列表，例如玩家列表和牌列表。 */
    private fun encodeList(values: List<String>): String {
        return values.joinToString(LIST_SEPARATOR) { encodeValue(it) }
    }

    /** 解码普通字符串列表；空字段表示空列表。 */
    private fun decodeList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        return value.split(LIST_SEPARATOR).map(::decodeValue)
    }

    /** 编码分数字段这类 String -> Int 的映射。 */
    private fun encodeIntMap(values: Map<String, Int>): String {
        return values.entries.joinToString(LIST_SEPARATOR) { (key, value) ->
            "${encodeValue(key)}$MAP_SEPARATOR$value"
        }
    }

    /** 解码 String -> Int 映射，使用最后一个冒号切分以兼容 key 中的编码内容。 */
    private fun decodeIntMap(value: String): Map<String, Int> {
        if (value.isEmpty()) return emptyMap()
        return value.split(LIST_SEPARATOR).associate { entry ->
            val index = entry.lastIndexOf(MAP_SEPARATOR)
            val key = decodeValue(entry.substring(0, index))
            val score = entry.substring(index + 1).toInt()
            key to score
        }
    }

    /** 编码 String -> List<String>，用于定向快照里的私人手牌。 */
    private fun encodeStringListMap(values: Map<String, List<String>>): String {
        return values.entries.joinToString(MAP_ENTRY_SEPARATOR) { (key, list) ->
            val encodedList = list.joinToString(NESTED_LIST_SEPARATOR) { encodeValue(it) }
            "${encodeValue(key)}$MAP_SEPARATOR$encodedList"
        }
    }

    /** 解码嵌套列表映射；空值表示该玩家没有私有列表。 */
    private fun decodeStringListMap(value: String): Map<String, List<String>> {
        if (value.isEmpty()) return emptyMap()
        return value.split(MAP_ENTRY_SEPARATOR).associate { entry ->
            val index = entry.indexOf(MAP_SEPARATOR)
            val key = decodeValue(entry.substring(0, index))
            val encodedList = entry.substring(index + 1)
            val list = if (encodedList.isEmpty()) {
                emptyList()
            } else {
                encodedList.split(NESTED_LIST_SEPARATOR).map(::decodeValue)
            }
            key to list
        }
    }

    /** URL 编码单个字段，防止分隔符出现在用户输入或错误文案中。 */
    private fun encodeValue(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    /** URL 解码单个字段。 */
    private fun decodeValue(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }

    /** 旧消息缺少规则字段时默认南方规则，保证向后兼容。 */
    private fun decodeRuleSetType(value: String?): RuleSetType {
        return value
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { RuleSetType.valueOf(it) }.getOrNull() }
            ?: RuleSetType.SOUTH
    }

    /** 字段分隔符和集合分隔符只在编码后的协议层使用，业务字段本身会先 URL 编码。 */
    private const val FIELD_SEPARATOR = "|"
    private const val LIST_SEPARATOR = ","
    private const val MAP_SEPARATOR = ":"
    private const val MAP_ENTRY_SEPARATOR = ";"
    private const val NESTED_LIST_SEPARATOR = "~"
}
