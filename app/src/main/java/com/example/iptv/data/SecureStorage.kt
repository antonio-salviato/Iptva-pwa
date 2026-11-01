package com.example.iptv.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.iptv.model.Channel
import com.example.iptv.model.ConnectionType
import com.example.iptv.model.XtreamAccountInfo
import com.example.iptv.model.XtreamCredentials
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

data class CachedChannel(
    val id: String,
    val name: String,
    val group: String,
    val logoUrl: String?,
    val streamUrl: String,
    val tvgId: String?,
    val lastWatched: Long = 0L
)

class SecureStorage(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    private val cacheFile by lazy {
        File(context.filesDir, "iptv_cached_channels_v2.json")
    }

    private val cacheFileTsv by lazy {
        File(context.filesDir, "iptv_cached_channels_v3.tsv")
    }

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val channelListType = Types.newParameterizedType(List::class.java, CachedChannel::class.java)
    private val channelListAdapter = moshi.adapter<List<CachedChannel>>(channelListType)

    fun saveUserCustomPlaylistUrl(url: String) {
        try {
            val trimmed = url.trim()
            prefs.edit()
                .putString(KEY_USER_PLAYLIST_URL, trimmed)
                .putString(KEY_PLAYLIST_URL, trimmed)
                .commit()
            Log.d("SecureStorage", "User custom playlist URL saved as default: $trimmed")
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error saving user custom playlist url", t)
        }
    }

    fun getUserCustomPlaylistUrl(): String? {
        return try {
            val url = prefs.getString(KEY_USER_PLAYLIST_URL, null)
            if (url.isNullOrBlank() || url == "http://demo.iptvcast.app/playlist.m3u") null else url
        } catch (t: Throwable) {
            null
        }
    }

    fun savePlaylistUrl(url: String) {
        try {
            val trimmed = url.trim()
            prefs.edit().putString(KEY_PLAYLIST_URL, trimmed).commit()
            Log.d("SecureStorage", "Playlist URL saved successfully: $trimmed")
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error saving playlist url", t)
        }
    }

    fun getPlaylistUrl(): String? {
        return try {
            val userUrl = getUserCustomPlaylistUrl()
            if (!userUrl.isNullOrBlank()) {
                return userUrl
            }
            val url = prefs.getString(KEY_PLAYLIST_URL, null)
            if (url.isNullOrBlank()) null else url
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error reading playlist url", t)
            null
        }
    }

    fun getMaskedPlaylistUrl(): String {
        val rawUrl = getPlaylistUrl() ?: return ""
        return maskUrl(rawUrl)
    }

    fun saveFavorites(favoriteIds: Set<String>) {
        try {
            prefs.edit().putStringSet(KEY_FAVORITES, favoriteIds).commit()
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error saving favorites", t)
        }
    }

    fun getFavorites(): Set<String> {
        return try {
            prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        } catch (t: Throwable) {
            emptySet()
        }
    }

    fun saveCachedChannels(channels: List<Channel>) {
        if (channels.isEmpty() || channels.any { it.id.startsWith("demo_") }) {
            return
        }
        try {
            // Write line-by-line using streaming BufferedWriter to avoid OutOfMemory with 10k+ channels
            val tempFile = File(context.filesDir, "iptv_cached_channels_v3.tmp")
            tempFile.bufferedWriter().use { writer ->
                for (ch in channels) {
                    val safeId = ch.id.replace('\t', ' ').replace('\n', ' ')
                    val safeName = ch.name.replace('\t', ' ').replace('\n', ' ')
                    val safeGroup = ch.group.replace('\t', ' ').replace('\n', ' ')
                    val safeLogo = (ch.logoUrl ?: "").replace('\t', ' ').replace('\n', ' ')
                    val safeStream = ch.streamUrl.replace('\t', ' ').replace('\n', ' ')
                    val safeTvg = (ch.tvgId ?: "").replace('\t', ' ').replace('\n', ' ')
                    val watched = ch.lastWatchedTimestamp
                    writer.write("$safeId\t$safeName\t$safeGroup\t$safeLogo\t$safeStream\t$safeTvg\t$watched\n")
                }
            }
            if (cacheFileTsv.exists()) cacheFileTsv.delete()
            tempFile.renameTo(cacheFileTsv)
            // Cleanup legacy large json file if present to free device storage
            if (cacheFile.exists()) cacheFile.delete()
            Log.d("SecureStorage", "Successfully stream-cached ${channels.size} channels to TSV")
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error caching channels to file", t)
        }
    }

    fun getCachedChannels(): List<Channel> {
        val favorites = getFavorites()
        val watchedIds = getWatchedIds()
        val list = ArrayList<Channel>()

        // 1. Try fast streaming TSV first
        if (cacheFileTsv.exists()) {
            try {
                cacheFileTsv.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val parts = line.split('\t')
                        if (parts.size >= 5) {
                            val id = parts[0]
                            if (id.startsWith("demo_")) continue
                            val name = parts[1]
                            val group = parts[2]
                            val logo = parts[3].ifBlank { null }
                            val stream = parts[4]
                            val tvg = parts.getOrNull(5)?.ifBlank { null }
                            val watched = parts.getOrNull(6)?.toLongOrNull() ?: 0L

                            list.add(
                                Channel(
                                    id = id,
                                    name = name,
                                    group = group,
                                    logoUrl = logo,
                                    streamUrl = stream,
                                    tvgId = tvg,
                                    isFavorite = favorites.contains(id),
                                    lastWatchedTimestamp = watched,
                                    isWatched = watchedIds.contains(id)
                                )
                            )
                        }
                    }
                }
                return list
            } catch (t: Throwable) {
                Log.e("SecureStorage", "Error reading streaming TSV cache", t)
            }
        }

        // 2. Legacy fallback to JSON if TSV doesn't exist yet
        return try {
            if (!cacheFile.exists()) return emptyList()
            val json = cacheFile.readText()
            if (json.isBlank()) return emptyList()
            val cachedList = channelListAdapter.fromJson(json) ?: return emptyList()
            val result = cachedList
                .filterNot { it.id.startsWith("demo_") }
                .map {
                    Channel(
                        id = it.id,
                        name = it.name,
                        group = it.group,
                        logoUrl = it.logoUrl,
                        streamUrl = it.streamUrl,
                        tvgId = it.tvgId,
                        isFavorite = favorites.contains(it.id),
                        lastWatchedTimestamp = it.lastWatched,
                        isWatched = watchedIds.contains(it.id)
                    )
                }
            if (result.isNotEmpty()) {
                saveCachedChannels(result)
            }
            result
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error reading cached channels from json file", t)
            emptyList()
        }
    }

    fun saveHistory(historyIds: List<String>) {
        try {
            val json = historyIds.take(30).joinToString(",")
            prefs.edit().putString(KEY_HISTORY, json).commit()
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error saving history", t)
        }
    }

    fun getHistoryIds(): List<String> {
        return try {
            val json = prefs.getString(KEY_HISTORY, "") ?: ""
            if (json.isBlank()) emptyList()
            else json.split(",").filter { it.isNotBlank() }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun setHttpWarningAcknowledged(ack: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_HTTP_ACK, ack).commit()
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error setting ack", t)
        }
    }

    fun isHttpWarningAcknowledged(): Boolean {
        return try {
            prefs.getBoolean(KEY_HTTP_ACK, false)
        } catch (t: Throwable) {
            false
        }
    }

    fun clearAll() {
        try {
            prefs.edit().clear().commit()
            if (cacheFile.exists()) cacheFile.delete()
            if (cacheFileTsv.exists()) cacheFileTsv.delete()
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error clearing prefs", t)
        }
    }

    fun markAsWatched(channelId: String) {
        try {
            val watched = getWatchedIds().toMutableSet()
            watched.add(channelId)
            prefs.edit().putStringSet(KEY_WATCHED, watched).commit()
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error marking as watched", t)
        }
    }

    fun getWatchedIds(): Set<String> {
        return try {
            prefs.getStringSet(KEY_WATCHED, emptySet()) ?: emptySet()
        } catch (t: Throwable) {
            emptySet()
        }
    }

    fun toggleWatched(channelId: String): Boolean {
        return try {
            val watched = getWatchedIds().toMutableSet()
            val isNowWatched = if (watched.contains(channelId)) {
                watched.remove(channelId)
                false
            } else {
                watched.add(channelId)
                true
            }
            prefs.edit().putStringSet(KEY_WATCHED, watched).commit()
            isNowWatched
        } catch (t: Throwable) {
            false
        }
    }

    fun saveConnectionType(type: ConnectionType) {
        try {
            prefs.edit().putString(KEY_CONNECTION_TYPE, type.name).commit()
            Log.d("SecureStorage", "Connection type saved: ${type.name}")
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error saving connection type", t)
        }
    }

    fun getConnectionType(): ConnectionType {
        return try {
            val name = prefs.getString(KEY_CONNECTION_TYPE, ConnectionType.M3U.name)
            ConnectionType.valueOf(name ?: ConnectionType.M3U.name)
        } catch (t: Throwable) {
            ConnectionType.M3U
        }
    }

    fun saveXtreamCredentials(credentials: XtreamCredentials) {
        try {
            prefs.edit()
                .putString(KEY_XTREAM_SERVER_URL, credentials.serverUrl.trim())
                .putString(KEY_XTREAM_USERNAME, credentials.username.trim())
                .putString(KEY_XTREAM_PASSWORD, credentials.password.trim())
                .commit()
            Log.d("SecureStorage", "Xtream credentials saved securely")
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error saving Xtream credentials", t)
        }
    }

    fun getXtreamCredentials(): XtreamCredentials? {
        return try {
            val server = prefs.getString(KEY_XTREAM_SERVER_URL, null)
            val user = prefs.getString(KEY_XTREAM_USERNAME, null)
            val pass = prefs.getString(KEY_XTREAM_PASSWORD, null)
            if (server.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank()) {
                null
            } else {
                XtreamCredentials(serverUrl = server, username = user, password = pass)
            }
        } catch (t: Throwable) {
            null
        }
    }

    fun saveXtreamAccountInfo(accountInfo: XtreamAccountInfo) {
        try {
            prefs.edit()
                .putString(KEY_XTREAM_STATUS, accountInfo.status)
                .putString(KEY_XTREAM_EXP_DATE, accountInfo.expDateFormatted)
                .putString(KEY_XTREAM_MAX_CONN, accountInfo.maxConnections)
                .commit()
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error saving Xtream account info", t)
        }
    }

    fun getXtreamAccountInfo(): XtreamAccountInfo? {
        return try {
            val creds = getXtreamCredentials() ?: return null
            val status = prefs.getString(KEY_XTREAM_STATUS, "Ativa") ?: "Ativa"
            val expDate = prefs.getString(KEY_XTREAM_EXP_DATE, "Ilimitado") ?: "Ilimitado"
            val maxConn = prefs.getString(KEY_XTREAM_MAX_CONN, "1") ?: "1"
            XtreamAccountInfo(
                status = status,
                expDateFormatted = expDate,
                maxConnections = maxConn,
                serverUrl = creds.serverUrl
            )
        } catch (t: Throwable) {
            null
        }
    }

    fun clearXtreamCredentials() {
        try {
            prefs.edit()
                .remove(KEY_XTREAM_SERVER_URL)
                .remove(KEY_XTREAM_USERNAME)
                .remove(KEY_XTREAM_PASSWORD)
                .remove(KEY_XTREAM_STATUS)
                .remove(KEY_XTREAM_EXP_DATE)
                .remove(KEY_XTREAM_MAX_CONN)
                .commit()
        } catch (t: Throwable) {
            Log.e("SecureStorage", "Error clearing Xtream credentials", t)
        }
    }

    companion object {
        private const val PREFS_FILENAME = "iptv_cast_user_prefs_v2"
        private const val KEY_USER_PLAYLIST_URL = "user_saved_custom_playlist_url"
        private const val KEY_PLAYLIST_URL = "saved_playlist_url"
        private const val KEY_FAVORITES = "favorite_channel_ids"
        private const val KEY_WATCHED = "watched_channel_ids"
        private const val KEY_HISTORY = "history_channel_ids"
        private const val KEY_HTTP_ACK = "http_ack"
        private const val KEY_CONNECTION_TYPE = "connection_type"
        private const val KEY_XTREAM_SERVER_URL = "xtream_server_url"
        private const val KEY_XTREAM_USERNAME = "xtream_username"
        private const val KEY_XTREAM_PASSWORD = "xtream_password"
        private const val KEY_XTREAM_EXP_DATE = "xtream_exp_date"
        private const val KEY_XTREAM_STATUS = "xtream_status"
        private const val KEY_XTREAM_MAX_CONN = "xtream_max_conn"

        fun maskUrl(url: String): String {
            if (url.isBlank()) return ""
            return try {
                val schemeEnd = url.indexOf("://")
                val scheme = if (schemeEnd != -1) url.substring(0, schemeEnd + 3) else ""
                val rest = if (schemeEnd != -1) url.substring(schemeEnd + 3) else url
                val domain = rest.substringBefore("/").substringBefore("?")
                
                var maskedQuery = ""
                if (url.contains("?")) {
                    val query = url.substringAfter("?")
                    val params = query.split("&").map { param ->
                        val parts = param.split("=")
                        if (parts.size == 2) {
                            val key = parts[0]
                            if (key.equals("password", ignoreCase = true) ||
                                key.equals("pass", ignoreCase = true) ||
                                key.equals("pw", ignoreCase = true) ||
                                key.equals("token", ignoreCase = true) ||
                                key.equals("key", ignoreCase = true)
                            ) {
                                "$key=*****"
                            } else {
                                param
                            }
                        } else {
                            param
                        }
                    }
                    maskedQuery = "?" + params.joinToString("&")
                }
                "$scheme$domain/...$maskedQuery"
            } catch (e: Exception) {
                "http://******/playlist.m3u"
            }
        }
    }
}
