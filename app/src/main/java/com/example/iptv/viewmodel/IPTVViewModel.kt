package com.example.iptv.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.cast.CastManager
import com.example.iptv.data.IPTVRepository
import com.example.iptv.model.CastState
import com.example.iptv.model.Channel
import com.example.iptv.model.PlaylistInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.example.iptv.model.ConnectionType
import com.example.iptv.model.XtreamAccountInfo
import com.example.iptv.model.XtreamCredentials

data class UpdateStatusDialogState(
    val title: String,
    val message: String
)

data class IPTVUiState(
    val channels: List<Channel> = emptyList(),
    val filteredChannels: List<Channel> = emptyList(),
    val historyChannels: List<Channel> = emptyList(),
    val categories: List<String> = listOf("Todos", "Favoritos", "Histórico"),
    val selectedCategory: String = "Todos",
    val searchQuery: String = "",
    val playlistInfo: PlaylistInfo? = null,
    val selectedChannel: Channel? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showPlaylistDialog: Boolean = false,
    val httpWarningAcknowledged: Boolean = false,
    val updateStatusDialog: UpdateStatusDialogState? = null,
    val activeConnectionType: ConnectionType = ConnectionType.M3U,
    val xtreamCredentials: XtreamCredentials? = null,
    val xtreamAccountInfo: XtreamAccountInfo? = null
)

class IPTVViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = IPTVRepository(application)
    private val updateRepository = com.example.iptv.data.UpdateCheckerRepository(application)

    private val _appUpdateInfo = MutableStateFlow<com.example.iptv.model.AppUpdateInfo?>(null)
    val appUpdateInfo: StateFlow<com.example.iptv.model.AppUpdateInfo?> = _appUpdateInfo.asStateFlow()
    
    val castManager: CastManager? by lazy {
        try {
            CastManager(application)
        } catch (t: Throwable) {
            Log.w("IPTVViewModel", "CastManager skipped: ${t.message}")
            null
        }
    }

    private val _fallbackCastState = MutableStateFlow(CastState())
    val castState: StateFlow<CastState> = castManager?.castState ?: _fallbackCastState.asStateFlow()

    private val _uiState = MutableStateFlow(IPTVUiState())
    val uiState: StateFlow<IPTVUiState> = _uiState.asStateFlow()

    init {
        val ack = repository.getSecureStorage().isHttpWarningAcknowledged()
        _uiState.update { it.copy(httpWarningAcknowledged = ack) }
        loadInitialChannels()
        checkAppUpdates()
    }

    fun checkAppUpdates(showToastIfUpToDate: Boolean = false) {
        viewModelScope.launch {
            if (showToastIfUpToDate) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            val result = updateRepository.checkForUpdates()
            if (showToastIfUpToDate) {
                _uiState.update { it.copy(isLoading = false) }
            }
            result.fold(
                onSuccess = { info ->
                    if (info.isUpdateAvailable) {
                        _appUpdateInfo.value = info
                    } else if (showToastIfUpToDate) {
                        _uiState.update {
                            it.copy(
                                updateStatusDialog = UpdateStatusDialogState(
                                    title = "Aplicativo Atualizado",
                                    message = "Você já está usando a versão mais recente do Firehair IPTV Player (v${com.example.BuildConfig.VERSION_NAME}). Nenhuma atualização pendente!"
                                )
                            )
                        }
                    }
                },
                onFailure = { error ->
                    if (showToastIfUpToDate) {
                        _uiState.update {
                            it.copy(
                                updateStatusDialog = UpdateStatusDialogState(
                                    title = "Aviso de Atualização",
                                    message = error.localizedMessage ?: "Não foi possível verificar atualizações no servidor GitHub."
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    fun clearUpdateStatusDialog() {
        _uiState.update { it.copy(updateStatusDialog = null) }
    }

    fun dismissUpdateDialog() {
        _appUpdateInfo.value = null
    }

    private fun loadInitialChannels() {
        val type = repository.getConnectionType()
        if (type == ConnectionType.XTREAM_CODES) {
            val creds = repository.getXtreamCredentials()
            val cachedChannels = repository.getCachedChannels()
            val accountInfo = repository.getXtreamAccountInfo()

            if (creds != null && cachedChannels.isNotEmpty()) {
                val info = PlaylistInfo(
                    url = creds.serverUrl,
                    channelCount = cachedChannels.size,
                    connectionType = ConnectionType.XTREAM_CODES,
                    serverAddress = creds.serverUrl,
                    xtreamAccountInfo = accountInfo
                )
                _uiState.update {
                    it.copy(
                        activeConnectionType = ConnectionType.XTREAM_CODES,
                        xtreamCredentials = creds,
                        xtreamAccountInfo = accountInfo
                    )
                }
                updateChannelsAndCategories(cachedChannels, info)
            } else if (creds != null) {
                connectXtream(creds.serverUrl, creds.username, creds.password)
            } else {
                loadDemoPlaylist()
            }
        } else {
            val userCustomUrl = repository.getUserCustomPlaylistUrl()
            val savedUrl = userCustomUrl ?: repository.getSavedPlaylistUrl()
            val isRealUserPlaylist = !savedUrl.isNullOrBlank() && savedUrl != "http://demo.iptvcast.app/playlist.m3u"

            if (isRealUserPlaylist) {
                val cachedChannels = repository.getCachedChannels()
                if (cachedChannels.isNotEmpty()) {
                    val maskedUrl = repository.getMaskedPlaylistUrl()
                    val info = PlaylistInfo(url = maskedUrl, channelCount = cachedChannels.size, connectionType = ConnectionType.M3U)
                    _uiState.update { it.copy(activeConnectionType = ConnectionType.M3U) }
                    updateChannelsAndCategories(cachedChannels, info)
                } else {
                    loadPlaylist(savedUrl!!)
                }
            } else {
                loadDemoPlaylist()
            }
        }
    }

    fun connectXtream(serverUrl: String, username: String, password: String) {
        val server = serverUrl.trim()
        val user = username.trim()
        val pass = password.trim()

        if (server.isBlank() || user.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Preencha todos os campos do servidor, usuário e senha.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, showPlaylistDialog = false) }
            val result = repository.fetchXtreamPlaylist(server, user, pass)
            result.fold(
                onSuccess = { (info, channels) ->
                    val creds = XtreamCredentials(server, user, pass)
                    updateChannelsAndCategories(channels, info)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeConnectionType = ConnectionType.XTREAM_CODES,
                            xtreamCredentials = creds,
                            xtreamAccountInfo = info.xtreamAccountInfo
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Erro ao conectar com o servidor Xtream Codes."
                        )
                    }
                }
            )
        }
    }

    fun refreshPlaylist() {
        val type = repository.getConnectionType()
        if (type == ConnectionType.XTREAM_CODES) {
            val creds = repository.getXtreamCredentials()
            if (creds == null) {
                _uiState.update { it.copy(errorMessage = "Nenhuma conta Xtream Codes configurada.") }
                return
            }
            connectXtream(creds.serverUrl, creds.username, creds.password)
        } else {
            val savedUrl = repository.getSavedPlaylistUrl()
            if (savedUrl.isNullOrBlank() || savedUrl == "http://demo.iptvcast.app/playlist.m3u") {
                _uiState.update { it.copy(errorMessage = "Para atualizar a lista, configure primeiro uma URL M3U válida ou Xtream Codes.") }
                return
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val result = repository.fetchPlaylist(savedUrl)
                result.fold(
                    onSuccess = { (info, channels) ->
                        val maskedInfo = info.copy(url = repository.getMaskedPlaylistUrl(), connectionType = ConnectionType.M3U)
                        updateChannelsAndCategories(channels, maskedInfo)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                activeConnectionType = ConnectionType.M3U,
                                errorMessage = "Lista atualizada com sucesso! (${channels.size} canais)"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Erro ao atualizar lista: ${error.localizedMessage}"
                            )
                        }
                    }
                )
            }
        }
    }

    fun testConnection() {
        val type = repository.getConnectionType()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            if (type == ConnectionType.XTREAM_CODES) {
                val creds = repository.getXtreamCredentials()
                if (creds == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Nenhuma credencial Xtream Codes salva.") }
                    return@launch
                }
                val result = repository.testXtreamConnection(creds.serverUrl, creds.username, creds.password)
                result.fold(
                    onSuccess = { account ->
                        repository.getSecureStorage().saveXtreamAccountInfo(account)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                xtreamAccountInfo = account,
                                updateStatusDialog = UpdateStatusDialogState(
                                    title = "Conexão Xtream OK!",
                                    message = "Servidor Xtream Codes online e autenticado com sucesso!\n\nStatus: ${account.status}\nExpiração: ${account.expDateFormatted}\nConexões Máximas: ${account.maxConnections}"
                                )
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Falha ao testar conexão Xtream Codes: ${error.localizedMessage}"
                            )
                        }
                    }
                )
            } else {
                val savedUrl = repository.getSavedPlaylistUrl()
                if (savedUrl.isNullOrBlank()) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Nenhuma URL M3U configurada.") }
                    return@launch
                }
                val result = repository.fetchPlaylist(savedUrl)
                result.fold(
                    onSuccess = { (info, channels) ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                updateStatusDialog = UpdateStatusDialogState(
                                    title = "Conexão M3U OK!",
                                    message = "Servidor de playlist M3U respondendo normalmente!\nCanais carregados: ${channels.size}"
                                )
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Falha no teste M3U: ${error.localizedMessage}"
                            )
                        }
                    }
                )
            }
        }
    }

    fun removeConnection() {
        repository.getSecureStorage().clearAll()
        _uiState.update {
            it.copy(
                channels = emptyList(),
                filteredChannels = emptyList(),
                historyChannels = emptyList(),
                playlistInfo = null,
                selectedChannel = null,
                isPlaying = false,
                activeConnectionType = ConnectionType.M3U,
                xtreamCredentials = null,
                xtreamAccountInfo = null,
                errorMessage = "Conexão removida com sucesso."
            )
        }
        loadDemoPlaylist()
    }

    fun loadPlaylist(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "A URL da playlist não pode ser vazia.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, showPlaylistDialog = false) }
            val result = repository.fetchPlaylist(url)
            result.fold(
                onSuccess = { (info, channels) ->
                    val maskedInfo = info.copy(url = repository.getMaskedPlaylistUrl())
                    updateChannelsAndCategories(channels, maskedInfo)
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Erro ao carregar a playlist. Verifique a URL."
                        )
                    }
                }
            )
        }
    }

    fun loadDemoPlaylist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, showPlaylistDialog = false) }
            val (info, channels) = repository.getDemoPlaylist()
            updateChannelsAndCategories(channels, info)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun updateChannelsAndCategories(channels: List<Channel>, info: PlaylistInfo) {
        val groups = channels.map { it.group }.distinct().sorted()
        val categories = mutableListOf("Todos", "Favoritos", "Histórico").apply {
            addAll(groups)
        }

        val historyIds = repository.getHistoryIds()
        val historyList = historyIds.mapNotNull { id -> channels.find { it.id == id } }

        _uiState.update { state ->
            val newFiltered = filterChannels(channels, historyList, state.selectedCategory, state.searchQuery)
            state.copy(
                channels = channels,
                historyChannels = historyList,
                categories = categories,
                playlistInfo = info,
                filteredChannels = newFiltered
            )
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { state ->
            val newFiltered = filterChannels(state.channels, state.historyChannels, category, state.searchQuery)
            state.copy(selectedCategory = category, filteredChannels = newFiltered)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val newFiltered = filterChannels(state.channels, state.historyChannels, state.selectedCategory, query)
            state.copy(searchQuery = query, filteredChannels = newFiltered)
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            val updatedFavs = repository.toggleFavorite(channel.id)
            _uiState.update { state ->
                val newChannels = state.channels.map {
                    if (it.id == channel.id) it.copy(isFavorite = updatedFavs.contains(it.id)) else it
                }
                val newHistory = state.historyChannels.map {
                    if (it.id == channel.id) it.copy(isFavorite = updatedFavs.contains(it.id)) else it
                }
                val selected = if (state.selectedChannel?.id == channel.id) {
                    state.selectedChannel.copy(isFavorite = updatedFavs.contains(channel.id))
                } else state.selectedChannel

                val filtered = filterChannels(newChannels, newHistory, state.selectedCategory, state.searchQuery)
                state.copy(
                    channels = newChannels,
                    historyChannels = newHistory,
                    selectedChannel = selected,
                    filteredChannels = filtered
                )
            }
        }
    }

    fun playChannel(channel: Channel) {
        repository.recordWatchHistory(channel.id)
        repository.markAsWatched(channel.id)

        _uiState.update { state ->
            val updatedChannels = state.channels.map {
                if (it.id == channel.id) it.copy(isWatched = true, lastWatchedTimestamp = System.currentTimeMillis()) else it
            }
            val historyIds = repository.getHistoryIds()
            val historyList = historyIds.mapNotNull { id -> updatedChannels.find { it.id == id } }
            val newFiltered = filterChannels(updatedChannels, historyList, state.selectedCategory, state.searchQuery)

            state.copy(
                channels = updatedChannels,
                filteredChannels = newFiltered,
                historyChannels = historyList,
                selectedChannel = channel.copy(isWatched = true),
                isPlaying = true
            )
        }

        try {
            if (castState.value.isConnected) {
                castManager?.castChannel(channel)
            }
        } catch (t: Throwable) {
            Log.w("IPTVViewModel", "Error casting channel: ${t.message}")
        }
    }

    fun toggleWatched(channel: Channel) {
        val isNowWatched = repository.toggleWatched(channel.id)
        _uiState.update { state ->
            val updatedChannels = state.channels.map {
                if (it.id == channel.id) it.copy(isWatched = isNowWatched) else it
            }
            val historyIds = repository.getHistoryIds()
            val historyList = historyIds.mapNotNull { id -> updatedChannels.find { it.id == id } }
            val newFiltered = filterChannels(updatedChannels, historyList, state.selectedCategory, state.searchQuery)
            state.copy(
                channels = updatedChannels,
                filteredChannels = newFiltered,
                historyChannels = historyList
            )
        }
    }

    fun nextChannel() {
        val currentList = _uiState.value.filteredChannels
        val current = _uiState.value.selectedChannel ?: return
        val currentIndex = currentList.indexOfFirst { it.id == current.id }
        if (currentIndex != -1 && currentIndex < currentList.size - 1) {
            playChannel(currentList[currentIndex + 1])
        } else if (currentList.isNotEmpty()) {
            playChannel(currentList.first())
        }
    }

    fun previousChannel() {
        val currentList = _uiState.value.filteredChannels
        val current = _uiState.value.selectedChannel ?: return
        val currentIndex = currentList.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            playChannel(currentList[currentIndex - 1])
        } else if (currentList.isNotEmpty()) {
            playChannel(currentList.last())
        }
    }

    fun closePlayer() {
        _uiState.update { it.copy(selectedChannel = null, isPlaying = false) }
    }

    fun openPlaylistDialog() {
        _uiState.update { it.copy(showPlaylistDialog = true) }
    }

    fun closePlaylistDialog() {
        _uiState.update { it.copy(showPlaylistDialog = false) }
    }

    fun acknowledgeHttpWarning() {
        repository.getSecureStorage().setHttpWarningAcknowledged(true)
        _uiState.update { it.copy(httpWarningAcknowledged = true) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun filterChannels(
        all: List<Channel>,
        history: List<Channel>,
        category: String,
        query: String
    ): List<Channel> {
        val baseList = when (category) {
            "Todos" -> all
            "Favoritos" -> all.filter { it.isFavorite }
            "Histórico" -> history
            else -> all.filter { it.group.equals(category, ignoreCase = true) }
        }

        return if (query.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.group.contains(query, ignoreCase = true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            castManager?.onDestroy()
        } catch (t: Throwable) {
            // Ignore
        }
    }
}
