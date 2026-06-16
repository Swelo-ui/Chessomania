package com.chessomania.app.net

data class SseEvent(
    val type: String,
    val username: String? = null,
    val status: String? = null,
    val from: String? = null,
    val by: String? = null,
    val gameId: String? = null,
    val white: String? = null,
    val black: String? = null,
    val challengeId: String? = null,
    val color: String? = null,
    val fen: String? = null,
    val san: String? = null,
    val winner: String? = null,
    val loser: String? = null,
    val to: String? = null,
    val promotion: String? = null
)

data class FriendInfo(
    val username: String,
    val status: String  // "online" | "offline" | "in_game"
)
