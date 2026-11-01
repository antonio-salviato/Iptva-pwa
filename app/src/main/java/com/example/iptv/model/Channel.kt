package com.example.iptv.model

import java.util.UUID

data class Channel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val group: String = "Geral",
    val logoUrl: String? = null,
    val streamUrl: String,
    val tvgId: String? = null,
    val isFavorite: Boolean = false,
    val lastWatchedTimestamp: Long = 0L,
    val isWatched: Boolean = false
)

data class PlaylistInfo(
    val url: String,
    val channelCount: Int,
    val lastUpdated: Long = System.currentTimeMillis(),
    val httpWarningAcknowledged: Boolean = false,
    val connectionType: ConnectionType = ConnectionType.M3U,
    val serverAddress: String? = null,
    val xtreamAccountInfo: XtreamAccountInfo? = null
)

data class CastState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val deviceName: String? = null,
    val currentChannel: Channel? = null
)
