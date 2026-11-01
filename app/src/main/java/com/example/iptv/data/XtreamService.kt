package com.example.iptv.data

import android.util.Log
import com.example.iptv.model.Channel
import com.example.iptv.model.XtreamAccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class XtreamStreamItem(
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val iconUrl: String?,
    val epgChannelId: String?
)

class XtreamService {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun cleanServerUrl(rawUrl: String): String {
        var trimmed = rawUrl.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed = "http://$trimmed"
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length - 1)
        }
        return trimmed
    }

    suspend fun fetchXtreamChannels(
        serverUrl: String,
        username: String,
        password: String
    ): Result<Pair<XtreamAccountInfo, List<Channel>>> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = cleanServerUrl(serverUrl)
            val cleanUser = username.trim()
            val cleanPass = password.trim()

            if (cleanUser.isBlank() || cleanPass.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Nome de usuário e senha não podem ser vazios."))
            }

            // Step 1: Authenticate and get account details
            val accountResult = authenticateAndGetAccountInfo(cleanUrl, cleanUser, cleanPass)
            if (accountResult.isFailure) {
                return@withContext Result.failure(accountResult.exceptionOrNull()!!)
            }
            val accountInfo = accountResult.getOrThrow()

            // Step 2: Fetch categories map
            val categoriesMap = getLiveCategories(cleanUrl, cleanUser, cleanPass).getOrDefault(emptyMap())

            // Step 3: Fetch live stream items
            val streamsResult = getLiveStreams(cleanUrl, cleanUser, cleanPass)
            if (streamsResult.isFailure) {
                return@withContext Result.failure(streamsResult.exceptionOrNull()!!)
            }
            val streamItems = streamsResult.getOrThrow()

            if (streamItems.isEmpty()) {
                return@withContext Result.failure(Exception("Nenhum canal ao vivo foi encontrado na conta Xtream Codes."))
            }

            // Map streams to channels
            val channels = streamItems.map { stream ->
                val categoryName = categoriesMap[stream.categoryId]?.ifBlank { null } ?: "Canais Ao Vivo"
                val streamUrl = "$cleanUrl/live/$cleanUser/$cleanPass/${stream.streamId}.m3u8"
                Channel(
                    id = "xtream_${stream.streamId}",
                    name = stream.name,
                    group = categoryName,
                    logoUrl = stream.iconUrl,
                    streamUrl = streamUrl,
                    tvgId = stream.epgChannelId
                )
            }

            Log.d("XtreamService", "Loaded ${channels.size} channels from Xtream Codes server $cleanUrl")
            Result.success(Pair(accountInfo, channels))
        } catch (e: Exception) {
            Log.e("XtreamService", "Error fetching Xtream channels", e)
            Result.failure(e)
        }
    }

    suspend fun authenticateAndGetAccountInfo(
        serverUrl: String,
        username: String,
        password: String
    ): Result<XtreamAccountInfo> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = cleanServerUrl(serverUrl)
            val apiUrl = "$cleanUrl/player_api.php?username=$username&password=$password"

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Erro no servidor Xtream Codes: HTTP ${response.code}"))
            }

            val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Resposta vazia do servidor Xtream Codes."))
            
            if (bodyString.trim().startsWith("<")) {
                return@withContext Result.failure(Exception("O servidor retornou uma página HTML em vez da API Xtream Codes. Verifique a URL e a porta."))
            }

            val json = JSONObject(bodyString)
            val userInfo = json.optJSONObject("user_info")
                ?: return@withContext Result.failure(Exception("Credenciais inválidas ou conta Xtream Codes não encontrada."))

            val authStatus = userInfo.optInt("auth", 1)
            val status = userInfo.optString("status", "Active")

            if (authStatus == 0 || status.equals("Disabled", ignoreCase = true) || status.equals("Expired", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Autenticação falhou. Status da conta: $status"))
            }

            val expDateRaw = userInfo.optString("exp_date", null)
            val expFormatted = formatExpDate(expDateRaw)
            val maxConnections = userInfo.optString("max_connections", "1")
            val activeConnections = userInfo.optString("active_cons", "0")

            val serverInfo = json.optJSONObject("server_info")
            val port = serverInfo?.optString("port", "8080") ?: "8080"
            val httpsPort = serverInfo?.optString("https_port", "8443") ?: "8443"
            val protocol = serverInfo?.optString("server_protocol", if (cleanUrl.startsWith("https")) "https" else "http") ?: "http"

            val accountInfo = XtreamAccountInfo(
                status = if (status.equals("Active", ignoreCase = true)) "Ativa" else status,
                expDateFormatted = expFormatted,
                maxConnections = maxConnections,
                activeConnections = activeConnections,
                serverUrl = cleanUrl,
                port = port,
                httpsPort = httpsPort,
                protocol = protocol
            )

            Result.success(accountInfo)
        } catch (e: Exception) {
            Result.failure(Exception("Erro de conexão com o servidor Xtream Codes: ${e.localizedMessage}"))
        }
    }

    private suspend fun getLiveCategories(
        serverUrl: String,
        username: String,
        password: String
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = cleanServerUrl(serverUrl)
            val apiUrl = "$cleanUrl/player_api.php?username=$username&password=$password&action=get_live_categories"

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.success(emptyMap())

            val bodyString = response.body?.string() ?: return@withContext Result.success(emptyMap())
            if (!bodyString.trim().startsWith("[")) return@withContext Result.success(emptyMap())

            val jsonArray = JSONArray(bodyString)
            val map = mutableMapOf<String, String>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val catId = item.optString("category_id")
                val catName = item.optString("category_name")
                if (catId.isNotBlank() && catName.isNotBlank()) {
                    map[catId] = catName
                }
            }

            Result.success(map)
        } catch (e: Exception) {
            Result.success(emptyMap())
        }
    }

    private suspend fun getLiveStreams(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<XtreamStreamItem>> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = cleanServerUrl(serverUrl)
            val apiUrl = "$cleanUrl/player_api.php?username=$username&password=$password&action=get_live_streams"

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Erro ao buscar lista de canais: HTTP ${response.code}"))
            }

            val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Lista de canais vazia."))
            if (!bodyString.trim().startsWith("[")) {
                return@withContext Result.failure(Exception("Formato inválido retornado pela API de canais."))
            }

            val jsonArray = JSONArray(bodyString)
            val items = mutableListOf<XtreamStreamItem>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val streamId = item.optInt("stream_id", item.optString("stream_id").toIntOrNull() ?: 0)
                if (streamId <= 0) continue

                val name = item.optString("name", "Canal $streamId")
                val categoryId = item.optString("category_id", "")
                val icon = item.optString("stream_icon").ifBlank { null }
                val epgId = item.optString("epg_channel_id").ifBlank { null }

                items.add(
                    XtreamStreamItem(
                        streamId = streamId,
                        name = name,
                        categoryId = categoryId,
                        iconUrl = icon,
                        epgChannelId = epgId
                    )
                )
            }

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchShortEpg(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = cleanServerUrl(serverUrl)
            val cleanStreamId = streamId.replace("xtream_", "")
            val apiUrl = "$cleanUrl/player_api.php?username=$username&password=$password&action=get_short_epg&stream_id=$cleanStreamId"

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.success(null)

            val bodyString = response.body?.string() ?: return@withContext Result.success(null)
            val json = JSONObject(bodyString)
            val listings = json.optJSONArray("epg_listings") ?: return@withContext Result.success(null)

            if (listings.length() > 0) {
                val first = listings.getJSONObject(0)
                val title = first.optString("title")
                val desc = first.optString("description")
                val epgText = if (desc.isNotBlank()) "$title — $desc" else title
                return@withContext Result.success(epgText.ifBlank { null })
            }

            Result.success(null)
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    private fun formatExpDate(expDateRaw: String?): String {
        if (expDateRaw.isNullOrBlank() || expDateRaw.equals("null", ignoreCase = true) || expDateRaw == "0") {
            return "Ilimitado"
        }
        val timestamp = expDateRaw.toLongOrNull() ?: return expDateRaw
        return try {
            val date = Date(timestamp * 1000L)
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            sdf.format(date)
        } catch (e: Exception) {
            expDateRaw
        }
    }
}
