package com.example.iptv.model

data class XtreamCredentials(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = ""
)

data class XtreamAccountInfo(
    val status: String = "Active",
    val expDateFormatted: String? = "Ilimitado",
    val maxConnections: String? = "1",
    val activeConnections: String? = "0",
    val serverUrl: String = "",
    val port: String = "8080",
    val httpsPort: String = "8443",
    val protocol: String = "http"
)
