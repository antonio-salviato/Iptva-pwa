package com.example.iptv.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.iptv.model.Channel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LiveRed

import kotlinx.coroutines.delay

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    channel: Channel,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    onClosePlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isLive by remember { mutableStateOf(false) }
    var maxLivePosition by remember { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var hasAttemptedTsFallback by remember { mutableStateOf(false) }

    // Resize modes: FIT, FILL, ZOOM
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        )
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Position & Live Status Polling
    LaunchedEffect(exoPlayer, isPlaying, showControls, isUserSeeking) {
        while (true) {
            if (!isUserSeeking) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = if (exoPlayer.duration > 0 && exoPlayer.duration != C.TIME_UNSET) exoPlayer.duration else 0L
                val live = exoPlayer.isCurrentMediaItemLive || exoPlayer.isCurrentMediaItemDynamic || dur <= 0L

                currentPosition = pos
                duration = dur
                isLive = live

                if (pos > maxLivePosition) {
                    maxLivePosition = pos
                }
            }
            delay(500)
        }
    }

    // Auto-hide controls when video starts playing or when controls are toggled on while playing
    LaunchedEffect(showControls, isPlaying, isBuffering, isUserSeeking) {
        if (showControls && isPlaying && !isBuffering && !isUserSeeking) {
            delay(3000)
            showControls = false
        }
    }

    // Prepare media item when channel streamUrl changes
    LaunchedEffect(channel.streamUrl) {
        isBuffering = true
        showControls = true
        errorMessage = null
        currentPosition = 0L
        duration = 0L
        maxLivePosition = 0L
        isLive = false
        isUserSeeking = false
        hasAttemptedTsFallback = false

        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "User-Agent" to userAgent
                )
            )

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpDataSourceFactory)
        val mediaItem = MediaItem.fromUri(Uri.parse(channel.streamUrl))
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    val currentView = LocalView.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window 
            ?: (currentView.context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    errorMessage = null
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("ExoPlayerView", "Playback Error: ${error.errorCodeName}", error)
                isBuffering = false
                if (!hasAttemptedTsFallback && channel.streamUrl.contains(".m3u8", ignoreCase = true)) {
                    hasAttemptedTsFallback = true
                    val tsUrl = channel.streamUrl.replace(".m3u8", ".ts")
                    Log.i("ExoPlayerView", "Retrying stream with MPEG-TS format: $tsUrl")
                    val mediaItem = MediaItem.fromUri(Uri.parse(tsUrl))
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                    return
                }
                errorMessage = "Falha ao reproduzir o canal. Código: ${error.errorCodeName}"
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(isFullscreen, isLandscape) {
        val window = (context as? Activity)?.window 
            ?: (currentView.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, currentView)
            if (isFullscreen || isLandscape) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val window = (context as? Activity)?.window 
                ?: (currentView.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, currentView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun toggleFullscreenMode() {
        if (isFullscreen || isLandscape) {
            isFullscreen = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            isFullscreen = true
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isFullscreen || isLandscape) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
            .background(Color.Black)
            .clickable { showControls = !showControls }
            .testTag("exoplayer_container")
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    resizeMode = resizeModes[resizeModeIndex]
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeModes[resizeModeIndex]
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Indicator
        if (isBuffering && errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = ElectricCyan,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Carregando transmissão...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        }

        // Error Banner Overlay with Retry
        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Erro de Reprodução",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ErrorRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            errorMessage = null
                            isBuffering = true
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Tentar novamente"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reconectar")
                    }
                }
            }
        }

        // Custom Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showControls = !showControls }
            ) {
                // Top Bar Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(LiveRed, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = channel.group,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        // Aspect Ratio Toggle
                        IconButton(onClick = {
                            resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                        }) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Proporção da Tela",
                                tint = Color.White
                            )
                        }

                        // Close Player
                        IconButton(onClick = onClosePlayer) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar Player",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Center Play/Pause & Prev/Next Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousChannel,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Canal Anterior",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .background(ElectricCyan, RoundedCornerShape(32.dp))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = onNextChannel,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Próximo Canal",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Bottom Bar Overlay with Progress Bar (Slider) and Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    val maxVal = if (isLive) maxLivePosition.toFloat() else duration.toFloat()
                    val displayMax = maxVal.coerceAtLeast(1f)
                    val currentVal = (if (isUserSeeking) seekPosition else currentPosition.toFloat())
                        .coerceIn(0f, displayMax)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current time label
                        Text(
                            text = formatTime(currentVal.toLong()),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Seek Slider
                        Slider(
                            value = currentVal,
                            onValueChange = { newValue ->
                                isUserSeeking = true
                                if (isLive) {
                                    // Restrict seeking forward beyond live head position
                                    seekPosition = newValue.coerceAtMost(maxLivePosition.toFloat())
                                } else {
                                    seekPosition = newValue.coerceIn(0f, displayMax)
                                }
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(seekPosition.toLong())
                                currentPosition = seekPosition.toLong()
                                isUserSeeking = false
                            },
                            valueRange = 0f..displayMax,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .testTag("video_progress_slider")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Duration or Live Badge
                        if (isLive) {
                            val isAtLiveEdge = currentPosition >= (maxLivePosition - 5000L) || maxLivePosition <= 0L
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        if (isAtLiveEdge) LiveRed.copy(alpha = 0.85f) else Color.DarkGray,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        if (maxLivePosition > 0L) {
                                            exoPlayer.seekTo(maxLivePosition)
                                            currentPosition = maxLivePosition
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.White, RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isAtLiveEdge) "AO VIVO" else "IR P/ AO VIVO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        } else {
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { toggleFullscreenMode() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen || isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Tela Cheia",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
