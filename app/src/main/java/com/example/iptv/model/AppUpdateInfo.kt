package com.example.iptv.model

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String = "",
    val apkUrl: String = "",
    val isUpdateAvailable: Boolean = false
)
