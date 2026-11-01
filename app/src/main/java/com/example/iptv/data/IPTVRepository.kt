package com.example.iptv.data

import android.content.Context
import com.example.iptv.model.Channel
import com.example.iptv.model.PlaylistInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

import com.example.iptv.model.ConnectionType
import com.example.iptv.model.XtreamAccountInfo
import com.example.iptv.model.XtreamCredentials

class IPTVRepository(private val context: Context) {

    private val secureStorage = SecureStorage(context)
    private val xtreamService = XtreamService()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getSecureStorage(): SecureStorage = secureStorage

    fun getConnectionType(): ConnectionType = secureStorage.getConnectionType()

    fun saveConnectionType(type: ConnectionType) = secureStorage.saveConnectionType(type)

    fun getXtreamCredentials(): XtreamCredentials? = secureStorage.getXtreamCredentials()

    fun getXtreamAccountInfo(): XtreamAccountInfo? = secureStorage.getXtreamAccountInfo()

    fun getUserCustomPlaylistUrl(): String? = secureStorage.getUserCustomPlaylistUrl()

    fun getSavedPlaylistUrl(): String? = secureStorage.getPlaylistUrl()

    fun getMaskedPlaylistUrl(): String = secureStorage.getMaskedPlaylistUrl()

    fun getCachedChannels(): List<Channel> = secureStorage.getCachedChannels()

    fun getFavorites(): Set<String> = secureStorage.getFavorites()

    fun getHistoryIds(): List<String> = secureStorage.getHistoryIds()

    suspend fun fetchXtreamPlaylist(
        serverUrl: String,
        username: String,
        password: String
    ): Result<Pair<PlaylistInfo, List<Channel>>> = withContext(Dispatchers.IO) {
        val result = xtreamService.fetchXtreamChannels(serverUrl, username, password)
        result.map { (accountInfo, rawChannels) ->
            val favorites = getFavorites()
            val watchedIds = getWatchedIds()
            val channelsWithFavs = rawChannels.map { channel ->
                val isFav = favorites.contains(channel.id)
                val isWat = watchedIds.contains(channel.id)
                if (isFav || isWat) channel.copy(isFavorite = isFav, isWatched = isWat) else channel
            }

            val credentials = XtreamCredentials(
                serverUrl = xtreamService.cleanServerUrl(serverUrl),
                username = username,
                password = password
            )

            // Save connection state & credentials securely
            secureStorage.saveConnectionType(ConnectionType.XTREAM_CODES)
            secureStorage.saveXtreamCredentials(credentials)
            secureStorage.saveXtreamAccountInfo(accountInfo)
            secureStorage.saveCachedChannels(channelsWithFavs)

            val info = PlaylistInfo(
                url = credentials.serverUrl,
                channelCount = channelsWithFavs.size,
                connectionType = ConnectionType.XTREAM_CODES,
                serverAddress = credentials.serverUrl,
                xtreamAccountInfo = accountInfo
            )

            Pair(info, channelsWithFavs)
        }
    }

    suspend fun testXtreamConnection(
        serverUrl: String,
        username: String,
        password: String
    ): Result<XtreamAccountInfo> = withContext(Dispatchers.IO) {
        xtreamService.authenticateAndGetAccountInfo(serverUrl, username, password)
    }

    suspend fun fetchPlaylist(url: String): Result<Pair<PlaylistInfo, List<Channel>>> = withContext(Dispatchers.IO) {
        try {
            val validatedUrl = url.trim()
            if (!validatedUrl.startsWith("http://", ignoreCase = true) && 
                !validatedUrl.startsWith("https://", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("URL deve começar com http:// ou https://"))
            }

            val request = Request.Builder()
                .url(validatedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Erro no servidor IPTV: HTTP ${response.code}"))
            }

            val responseBody = response.body ?: return@withContext Result.failure(Exception("A resposta da playlist M3U está vazia."))
            val bufferedReader = responseBody.charStream().buffered(65536)

            bufferedReader.mark(8192)
            val headerBuffer = CharArray(2048)
            val charsRead = bufferedReader.read(headerBuffer, 0, 2048)
            bufferedReader.reset()
            val headerSample = if (charsRead > 0) String(headerBuffer, 0, charsRead) else ""

            if (!headerSample.contains("#EXTINF", ignoreCase = true) && 
                (validatedUrl.endsWith(".m3u8", ignoreCase = true) || headerSample.contains("#EXT-X-STREAM-INF", ignoreCase = true))) {
                val singleChannel = Channel(
                    name = "Stream HLS Direto",
                    group = "Stream Individual",
                    streamUrl = validatedUrl,
                    logoUrl = null
                )
                val channels = listOf(singleChannel)
                val info = PlaylistInfo(url = validatedUrl, channelCount = 1)
                secureStorage.saveUserCustomPlaylistUrl(validatedUrl)
                secureStorage.savePlaylistUrl(validatedUrl)
                secureStorage.saveCachedChannels(channels)
                return@withContext Result.success(Pair(info, channels))
            }

            val parsedChannels = M3uParser.parse(bufferedReader)
            if (parsedChannels.isEmpty()) {
                return@withContext Result.failure(Exception("Nenhum canal válido foi encontrado na playlist M3U."))
            }

            val favorites = getFavorites()
            val watchedIds = getWatchedIds()
            val channelsWithFavs = parsedChannels.map { channel ->
                val isFav = favorites.contains(channel.id)
                val isWat = watchedIds.contains(channel.id)
                if (isFav || isWat) channel.copy(isFavorite = isFav, isWatched = isWat) else channel
            }

            val playlistInfo = PlaylistInfo(
                url = validatedUrl,
                channelCount = channelsWithFavs.size,
                connectionType = ConnectionType.M3U
            )

            // Save to secure storage as user custom playlist
            secureStorage.saveConnectionType(ConnectionType.M3U)
            secureStorage.saveUserCustomPlaylistUrl(validatedUrl)
            secureStorage.savePlaylistUrl(validatedUrl)
            secureStorage.saveCachedChannels(channelsWithFavs)

            Result.success(Pair(playlistInfo, channelsWithFavs))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDemoPlaylist(): Pair<PlaylistInfo, List<Channel>> {
        val favorites = getFavorites()
        val demoChannels = listOf(
            Channel(
                id = "demo_nasa_tv",
                name = "NASA TV Live (Espaço & Ciência)",
                group = "Canais Ao Vivo",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/e/e5/NASA_logo.svg",
                streamUrl = "https://ntv1.akamaized.net/hls/live/2014075/NASA-TV-HD/master.m3u8"
            ),
            Channel(
                id = "demo_redbull_tv",
                name = "Red Bull TV (Esportes & Eventos)",
                group = "Esportes & Ação",
                logoUrl = "https://upload.wikimedia.org/wikipedia/en/2/2f/Red_Bull_TV_logo.svg",
                streamUrl = "https://rbmn-live.akamaized.net/hls/live/591070/FLO_HLS_S0/master.m3u8"
            ),
            Channel(
                id = "demo_dw_portugues",
                name = "DW Notícias (Ao Vivo)",
                group = "Notícias",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/7/75/Deutsche_Welle_logo.svg",
                streamUrl = "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8"
            ),
            Channel(
                id = "demo_euronews_pt",
                name = "Euronews Português",
                group = "Notícias",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/1/18/Euronews_2016_logo.svg",
                streamUrl = "https://euronews-euronews-portuguese-1-us.samsung.wurl.tv/playlist.m3u8"
            ),
            Channel(
                id = "demo_bbb_hls",
                name = "Big Buck Bunny 1080p (Filme)",
                group = "Filmes & Demo",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_buck_bunny_poster_big.jpg",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
            ),
            Channel(
                id = "demo_sintel_hls",
                name = "Sintel Movie Stream (Filme)",
                group = "Filmes & Demo",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/a/a7/Sintel_poster.jpg",
                streamUrl = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
            ),
            Channel(
                id = "demo_tears_hls",
                name = "Tears of Steel 4K (Filme)",
                group = "Filmes & Demo",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/2/23/Tears_of_Steel_poster.jpg",
                streamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
            ),
            Channel(
                id = "demo_apple_bipbop",
                name = "Apple Test Live Stream (HLS)",
                group = "Canais Teste",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/f/fa/Apple_logo_black.svg",
                streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
            )
        ).map { channel ->
            channel.copy(isFavorite = favorites.contains(channel.id))
        }

        val info = PlaylistInfo(
            url = "http://demo.iptvcast.app/playlist.m3u",
            channelCount = demoChannels.size
        )

        return Pair(info, demoChannels)
    }

    fun toggleFavorite(channelId: String): Set<String> {
        val current = getFavorites().toMutableSet()
        if (current.contains(channelId)) {
            current.remove(channelId)
        } else {
            current.add(channelId)
        }
        secureStorage.saveFavorites(current)
        return current
    }

    fun recordWatchHistory(channelId: String) {
        val currentHistory = getHistoryIds().toMutableList()
        currentHistory.remove(channelId)
        currentHistory.add(0, channelId)
        secureStorage.saveHistory(currentHistory)
    }

    fun markAsWatched(channelId: String) {
        secureStorage.markAsWatched(channelId)
    }

    fun getWatchedIds(): Set<String> {
        return secureStorage.getWatchedIds()
    }

    fun toggleWatched(channelId: String): Boolean {
        return secureStorage.toggleWatched(channelId)
    }
}
