package com.scut.chudadi.network

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object BluetoothMessageCodec {
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
                encodeList(message.bluetoothPlayers)
            )
            is BluetoothMessage.StartGame -> join("START", message.seed.toString())
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
                message.lastWinnerId.orEmpty()
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

    fun decode(line: String): BluetoothMessage? {
        val parts = line.split(FIELD_SEPARATOR)
        val type = parts.firstOrNull() ?: return null
        val fields = parts.drop(1).map(::decodeValue)

        return runCatching {
            when (type) {
                "JOIN" -> BluetoothMessage.JoinRoom(fields[0], fields[1])
                "SEAT" -> BluetoothMessage.SeatAssigned(fields[0], fields[1], decodeList(fields[2]))
                "READY" -> BluetoothMessage.Ready(fields[0])
                "ROOM" -> BluetoothMessage.RoomState(
                    players = decodeList(fields[0]),
                    readyPlayers = fields.getOrNull(1)?.let(::decodeList).orEmpty(),
                    bluetoothPlayers = fields.getOrNull(2)?.let(::decodeList).orEmpty()
                )
                "START" -> BluetoothMessage.StartGame(fields[0].toLong())
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
                    lastWinnerId = fields[9].ifEmpty { null }
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

    private fun join(type: String, vararg fields: String): String {
        return buildList {
            add(type)
            fields.forEach { add(encodeValue(it)) }
        }.joinToString(FIELD_SEPARATOR)
    }

    private fun encodeList(values: List<String>): String {
        return values.joinToString(LIST_SEPARATOR) { encodeValue(it) }
    }

    private fun decodeList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        return value.split(LIST_SEPARATOR).map(::decodeValue)
    }

    private fun encodeIntMap(values: Map<String, Int>): String {
        return values.entries.joinToString(LIST_SEPARATOR) { (key, value) ->
            "${encodeValue(key)}$MAP_SEPARATOR$value"
        }
    }

    private fun decodeIntMap(value: String): Map<String, Int> {
        if (value.isEmpty()) return emptyMap()
        return value.split(LIST_SEPARATOR).associate { entry ->
            val index = entry.lastIndexOf(MAP_SEPARATOR)
            val key = decodeValue(entry.substring(0, index))
            val score = entry.substring(index + 1).toInt()
            key to score
        }
    }

    private fun encodeStringListMap(values: Map<String, List<String>>): String {
        return values.entries.joinToString(MAP_ENTRY_SEPARATOR) { (key, list) ->
            val encodedList = list.joinToString(NESTED_LIST_SEPARATOR) { encodeValue(it) }
            "${encodeValue(key)}$MAP_SEPARATOR$encodedList"
        }
    }

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

    private fun encodeValue(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun decodeValue(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }

    private const val FIELD_SEPARATOR = "|"
    private const val LIST_SEPARATOR = ","
    private const val MAP_SEPARATOR = ":"
    private const val MAP_ENTRY_SEPARATOR = ";"
    private const val NESTED_LIST_SEPARATOR = "~"
}
