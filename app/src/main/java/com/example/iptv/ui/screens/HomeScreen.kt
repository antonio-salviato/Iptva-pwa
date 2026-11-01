package com.example.iptv.ui.screens

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.model.Channel
import com.example.iptv.ui.components.CastDeviceDialog
import com.example.iptv.ui.components.CastStatusBar
import com.example.iptv.ui.components.CategoryChipRow
import com.example.iptv.ui.components.ChannelRowCard
import com.example.iptv.ui.components.ExoPlayerView
import com.example.iptv.ui.components.FolderCard
import com.example.iptv.ui.components.FolderHeaderCard
import com.example.iptv.ui.components.NativeCastButton
import com.example.iptv.ui.components.PlaylistSetupDialog
import com.example.iptv.viewmodel.IPTVViewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: IPTVViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val castState by viewModel.castState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isFolderViewMode by remember { mutableStateOf(false) }
    var showCastDeviceDialog by remember { mutableStateOf(false) }

    // Map to track expanded states of folders in Folder View Mode
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(castState.isConnected) {
        if (castState.isConnected && uiState.selectedChannel != null) {
            viewModel.castManager?.castChannel(uiState.selectedChannel!!)
        }
    }

    if (showCastDeviceDialog && viewModel.castManager != null) {
        CastDeviceDialog(
            castManager = viewModel.castManager!!,
            currentChannel = uiState.selectedChannel,
            onDismiss = { showCastDeviceDialog = false }
        )
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearErrorMessage()
        }
    }

    val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()

    appUpdateInfo?.let { updateInfo ->
        com.example.iptv.ui.components.UpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    uiState.updateStatusDialog?.let { dialogState ->
        AlertDialog(
            onDismissRequest = { viewModel.clearUpdateStatusDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = dialogState.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = dialogState.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearUpdateStatusDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(isLandscape, uiState.selectedChannel) {
        val window = (context as? Activity)?.window ?: (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (isLandscape && uiState.selectedChannel != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val window = (context as? Activity)?.window ?: (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (uiState.showPlaylistDialog) {
        PlaylistSetupDialog(
            playlistInfo = uiState.playlistInfo,
            activeConnectionType = uiState.activeConnectionType,
            xtreamCredentials = uiState.xtreamCredentials,
            xtreamAccountInfo = uiState.xtreamAccountInfo,
            onSaveM3uUrl = { url -> viewModel.loadPlaylist(url) },
            onConnectXtream = { server, user, pass -> viewModel.connectXtream(server, user, pass) },
            onTestConnection = { viewModel.testConnection() },
            onRefreshList = { viewModel.refreshPlaylist() },
            onRemoveConnection = { viewModel.removeConnection() },
            onLoadDemo = { viewModel.loadDemoPlaylist() },
            onCheckUpdate = { viewModel.checkAppUpdates(showToastIfUpToDate = true) },
            onDismiss = { viewModel.closePlaylistDialog() }
        )
    }

    val showLandscapeFullscreen = isLandscape && uiState.selectedChannel != null && !castState.isConnected

    if (showLandscapeFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            ExoPlayerView(
                channel = uiState.selectedChannel!!,
                onNextChannel = { viewModel.nextChannel() },
                onPreviousChannel = { viewModel.previousChannel() },
                onClosePlayer = { viewModel.closePlayer() },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        BackHandler {
                            viewModel.setSearchQuery("")
                            isSearchExpanded = false
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(23.dp))
                                .background(MidnightSurfaceVariant)
                                .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(23.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (uiState.searchQuery.isEmpty()) {
                                        Text(
                                            text = "Pesquisar canais...",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                    BasicTextField(
                                        value = uiState.searchQuery,
                                        onValueChange = { viewModel.setSearchQuery(it) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(ElectricCyan),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("search_input")
                                    )
                                }
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.setSearchQuery("") },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Limpar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.setSearchQuery("")
                                        isSearchExpanded = false
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Fechar Pesquisa",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = ElectricCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "IPTV Cast",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                                uiState.playlistInfo?.let { info ->
                                    Text(
                                        text = "${info.channelCount} canais",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (!isSearchExpanded) {
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Pesquisar")
                        }

                        // Refresh Playlist Button
                        IconButton(onClick = { viewModel.refreshPlaylist() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Atualizar Lista",
                                tint = ElectricCyan
                            )
                        }

                        // Toggle View Mode (Single List vs Folder View)
                        IconButton(onClick = { isFolderViewMode = !isFolderViewMode }) {
                            Icon(
                                imageVector = if (isFolderViewMode) Icons.Default.ViewList else Icons.Default.Folder,
                                contentDescription = if (isFolderViewMode) "Modo Lista" else "Modo Pastas",
                                tint = if (isFolderViewMode) ElectricCyan else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Native Google Cast Chromecast button
                        NativeCastButton(
                            castState = castState,
                            onClick = { showCastDeviceDialog = true }
                        )

                        IconButton(onClick = { viewModel.openPlaylistDialog() }) {
                            Icon(Icons.Default.Settings, contentDescription = "Configurar Lista")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MidnightSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Chromecast Connection Status Bar
            CastStatusBar(
                castState = castState,
                onPlayPause = { viewModel.castManager?.playPauseRemote() },
                onStop = { viewModel.castManager?.stopRemote() },
                onDisconnect = { viewModel.castManager?.disconnect() }
            )

            // Dedicated Player Section (when channel is active and not casting)
            uiState.selectedChannel?.let { channel ->
                if (!castState.isConnected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        ExoPlayerView(
                            channel = channel,
                            onNextChannel = { viewModel.nextChannel() },
                            onPreviousChannel = { viewModel.previousChannel() },
                            onClosePlayer = { viewModel.closePlayer() }
                        )
                    }
                }
            }

            // Categories
            CategoryChipRow(
                categories = uiState.categories,
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = { viewModel.selectCategory(it) }
            )

            // Recently Played Carousel (when in "Todos" or "Histórico")
            if (uiState.historyChannels.isNotEmpty() && uiState.searchQuery.isBlank() && uiState.selectedCategory == "Todos") {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Histórico Recente",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.historyChannels, key = { "hist_${it.id}" }) { channel ->
                            HistoryChip(
                                channel = channel,
                                onChannelClick = { viewModel.playChannel(it) }
                            )
                        }
                    }
                }
            }

            // Loading state
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ElectricCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Processando playlist M3U...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (uiState.filteredChannels.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TvOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "Nenhum canal encontrado para \"${uiState.searchQuery}\""
                            else if (uiState.selectedCategory == "Favoritos") "Nenhum canal favoritado ainda"
                            else "Nenhum canal disponível",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Configure uma playlist M3U ou recarregue a lista para começar a assistir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (isFolderViewMode && uiState.selectedCategory == "Todos" && uiState.searchQuery.isBlank()) {
                // FOLDER VIEW MODE (Organized by Category Folders)
                val groupedChannels = remember(uiState.filteredChannels) {
                    uiState.filteredChannels.groupBy { it.group }
                }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    groupedChannels.forEach { (groupName, groupList) ->
                        val isExpanded = expandedFolders[groupName] ?: false

                        item(key = "folder_header_$groupName", contentType = "folder_header") {
                            FolderHeaderCard(
                                groupName = groupName,
                                channelCount = groupList.size,
                                isExpanded = isExpanded,
                                onFolderClick = {
                                    expandedFolders[groupName] = !isExpanded
                                }
                            )
                        }

                        if (isExpanded) {
                            items(
                                items = groupList,
                                key = { "f_${groupName}_${it.id}" },
                                contentType = { "channel_row" }
                            ) { channel ->
                                val isSelected = uiState.selectedChannel?.id == channel.id
                                Box(modifier = Modifier.padding(start = 10.dp)) {
                                    ChannelRowCard(
                                        channel = channel,
                                        isSelected = isSelected,
                                        onChannelClick = { viewModel.playChannel(it) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // SINGLE COLUMN LIST MODE (1 Channel Per Line with Full Names)
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(uiState.filteredChannels, key = { it.id }) { channel ->
                        val isSelected = uiState.selectedChannel?.id == channel.id
                        ChannelRowCard(
                            channel = channel,
                            isSelected = isSelected,
                            onChannelClick = { viewModel.playChannel(it) },
                            onFavoriteToggle = { viewModel.toggleFavorite(it) }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun HistoryChip(
    channel: Channel,
    onChannelClick: (Channel) -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChannelClick(channel) },
        colors = CardDefaults.cardColors(containerColor = MidnightSurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                color = ElectricCyan.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.group,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
