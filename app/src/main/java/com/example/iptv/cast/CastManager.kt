package com.example.iptv.cast

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.example.iptv.model.CastState
import com.example.iptv.model.Channel
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiscoveredCastDevice(
    val id: String,
    val name: String,
    val description: String?,
    val isConnected: Boolean,
    val route: MediaRouter.RouteInfo? = null,
    val rokuIp: String? = null
)

class CastManager(private val context: Context) {

    val rokuManager = RokuDeviceManager(context)
    private var castContext: CastContext? = null
    private var currentSession: CastSession? = null
    private var currentRokuIp: String? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var scanJob: Job? = null

    private val mediaRouter: MediaRouter by lazy {
        MediaRouter.getInstance(context.applicationContext)
    }

    val mediaRouteSelector: MediaRouteSelector by lazy {
        val builder = MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)

        try {
            castContext?.mergedSelector?.let { merged ->
                for (category in merged.controlCategories) {
                    builder.addControlCategory(category)
                }
            }
        } catch (e: Exception) {
            Log.w("CastManager", "Error querying mergedSelector: ${e.message}")
        }
        builder.build()
    }

    private val _castState = MutableStateFlow(CastState())
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredCastDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredCastDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Log.d("CastManager", "Route Added: ${route.name} (${route.description})")
            updateDiscoveredRoutes()
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Log.d("CastManager", "Route Removed: ${route.name}")
            updateDiscoveredRoutes()
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateDiscoveredRoutes()
        }

        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Log.d("CastManager", "Route Selected: ${route.name}")
            val name = route.name.toString()
            _castState.value = _castState.value.copy(
                isConnected = !route.isDefault && !route.isDefaultOrBluetooth,
                deviceName = name
            )
            updateDiscoveredRoutes()
        }

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Log.d("CastManager", "Route Unselected: ${route.name}")
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                deviceName = null
            )
            updateDiscoveredRoutes()
        }
    }

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _castState.value = _castState.value.copy(isConnecting = true)
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            currentSession = session
            val deviceName = session.castDevice?.friendlyName ?: "Chromecast / Smart TV"
            _castState.value = _castState.value.copy(
                isConnected = true,
                isConnecting = false,
                deviceName = deviceName
            )
            Log.d("CastManager", "Cast Session Started: $deviceName")
            updateDiscoveredRoutes()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _castState.value = CastState(isConnected = false, isConnecting = false)
            Log.e("CastManager", "Cast Session Start Failed code: $error")
            updateDiscoveredRoutes()
        }

        override fun onSessionEnding(session: CastSession) {
            _castState.value = _castState.value.copy(isConnecting = false)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            currentSession = null
            _castState.value = CastState(isConnected = false, isConnecting = false)
            Log.d("CastManager", "Cast Session Ended")
            updateDiscoveredRoutes()
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _castState.value = _castState.value.copy(isConnecting = true)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            currentSession = session
            val deviceName = session.castDevice?.friendlyName ?: "Chromecast / Smart TV"
            _castState.value = _castState.value.copy(
                isConnected = true,
                isConnecting = false,
                deviceName = deviceName
            )
            updateDiscoveredRoutes()
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _castState.value = CastState(isConnected = false, isConnecting = false)
            updateDiscoveredRoutes()
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _castState.value = _castState.value.copy(isConnecting = true)
        }
    }

    init {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("iptv_cast_multicast_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d("CastManager", "MulticastLock acquired for local network device discovery")
        } catch (t: Throwable) {
            Log.w("CastManager", "Failed to acquire MulticastLock: ${t.message}")
        }

        try {
            castContext = CastContext.getSharedInstance(context.applicationContext)
            castContext?.sessionManager?.addSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java
            )
            val session = castContext?.sessionManager?.currentCastSession
            if (session != null && session.isConnected) {
                currentSession = session
                _castState.value = CastState(
                    isConnected = true,
                    deviceName = session.castDevice?.friendlyName ?: "Chromecast / Smart TV"
                )
            }
        } catch (t: Throwable) {
            Log.w("CastManager", "Google Play Services Cast unavailable: ${t.message}")
        }

        // Start passive scan on initialization so routes are warmed up
        try {
            mediaRouter.addCallback(
                mediaRouteSelector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )
            updateDiscoveredRoutes()
        } catch (t: Throwable) {
            Log.w("CastManager", "Error setting initial MediaRouter callback: ${t.message}")
        }
    }

    fun startActiveScan() {
        _isScanning.value = true
        try {
            if (multicastLock?.isHeld != true) {
                multicastLock?.acquire()
            }
            mediaRouter.removeCallback(mediaRouterCallback)
            mediaRouter.addCallback(
                mediaRouteSelector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN or MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )
            updateDiscoveredRoutes()

            // Run SSDP & network scan for Roku devices concurrently
            scanJob?.cancel()
            scanJob = scope.launch {
                repeat(4) {
                    rokuManager.discoverRokuDevices()
                    updateDiscoveredRoutes()
                    delay(1500)
                }
                _isScanning.value = false
            }
        } catch (t: Throwable) {
            Log.e("CastManager", "Error starting active scan: ${t.message}", t)
            _isScanning.value = false
        }
    }

    fun addManualRokuIp(ip: String) {
        scope.launch {
            _isScanning.value = true
            rokuManager.addManualRokuIp(ip)
            updateDiscoveredRoutes()
            _isScanning.value = false
        }
    }

    fun stopActiveScan() {
        _isScanning.value = false
        scanJob?.cancel()
        try {
            mediaRouter.removeCallback(mediaRouterCallback)
            mediaRouter.addCallback(
                mediaRouteSelector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )
        } catch (t: Throwable) {
            Log.w("CastManager", "Error resetting scan callback: ${t.message}")
        }
    }

    fun selectDevice(device: DiscoveredCastDevice, currentChannelToCast: Channel? = null) {
        try {
            if (device.rokuIp != null) {
                currentRokuIp = device.rokuIp
                _castState.value = _castState.value.copy(
                    isConnected = true,
                    isConnecting = false,
                    deviceName = device.name
                )
                Log.d("CastManager", "Selected Roku device at IP: ${device.rokuIp}")
                updateDiscoveredRoutes()

                if (currentChannelToCast != null) {
                    castChannel(currentChannelToCast)
                }
            } else if (device.route != null) {
                currentRokuIp = null
                _castState.value = _castState.value.copy(isConnecting = true, deviceName = device.name)
                mediaRouter.selectRoute(device.route)
                Log.d("CastManager", "Selected route: ${device.name}")
            }
        } catch (t: Throwable) {
            Log.e("CastManager", "Error selecting route: ${t.message}", t)
            _castState.value = _castState.value.copy(isConnecting = false)
        }
    }

    fun updateDiscoveredRoutes() {
        try {
            val selected = mediaRouter.selectedRoute
            val currentConnectedId = if (!selected.isDefault && !selected.isDefaultOrBluetooth) selected.id else null

            val list = mutableListOf<DiscoveredCastDevice>()

            // 1. Add Chromecast / MediaRouter devices
            val matching = mediaRouter.routes.filter { route ->
                !route.isDefault &&
                !route.isDefaultOrBluetooth &&
                (route.matchesSelector(mediaRouteSelector) ||
                 route.description?.contains("Cast", ignoreCase = true) == true ||
                 route.description?.contains("Google", ignoreCase = true) == true ||
                 route.description?.contains("TV", ignoreCase = true) == true)
            }

            for (route in matching) {
                list.add(
                    DiscoveredCastDevice(
                        id = route.id,
                        name = route.name.toString(),
                        description = route.description?.toString() ?: "Google Cast / Smart TV",
                        isConnected = (route.id == currentConnectedId || (_castState.value.isConnected && route.name.toString() == _castState.value.deviceName)),
                        route = route
                    )
                )
            }

            // 2. Add Roku devices detected via ECP / SSDP
            val rokuDevices = rokuManager.getDiscoveredDevices()
            for (roku in rokuDevices) {
                val isConn = (currentRokuIp == roku.ip) || (_castState.value.isConnected && _castState.value.deviceName == roku.friendlyName)
                list.add(
                    DiscoveredCastDevice(
                        id = "roku_${roku.ip}",
                        name = roku.friendlyName,
                        description = "${roku.modelName} (${roku.ip})",
                        isConnected = isConn,
                        rokuIp = roku.ip
                    )
                )
            }

            _discoveredDevices.value = list
        } catch (t: Throwable) {
            Log.e("CastManager", "Error updating discovered routes: ${t.message}")
        }
    }

    fun castChannel(channel: Channel) {
        try {
            val rokuIp = currentRokuIp
            if (rokuIp != null) {
                scope.launch {
                    val result = rokuManager.playStreamOnRoku(rokuIp, channel.streamUrl, channel.name)
                    if (result == RokuPlayResult.SUCCESS) {
                        _castState.value = _castState.value.copy(currentChannel = channel, isConnected = true)
                    } else if (result == RokuPlayResult.NEEDS_APP_INSTALL) {
                        Log.i("CastManager", "Roku TV needs receiver installed - opened Channel Store on TV")
                    }
                }
                return
            }

            val session = currentSession
            if (session == null || !session.isConnected) {
                Log.w("CastManager", "Cannot cast: No active Chromecast session")
                return
            }

            val remoteMediaClient: RemoteMediaClient = session.remoteMediaClient ?: return

            val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, channel.name)
                putString(MediaMetadata.KEY_SUBTITLE, "Grupo: ${channel.group}")
                channel.logoUrl?.let { logo ->
                    if (logo.startsWith("http")) {
                        addImage(WebImage(Uri.parse(logo)))
                    }
                }
            }

            val contentType = when {
                channel.streamUrl.contains(".m3u8", ignoreCase = true) -> "application/x-mpegurl"
                channel.streamUrl.contains(".ts", ignoreCase = true) -> "video/mp2t"
                else -> "application/x-mpegurl"
            }

            val mediaInfo = MediaInfo.Builder(channel.streamUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setContentType(contentType)
                .setMetadata(movieMetadata)
                .build()

            val requestData = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .build()

            remoteMediaClient.load(requestData)
            _castState.value = _castState.value.copy(currentChannel = channel)
        } catch (t: Throwable) {
            Log.e("CastManager", "Error casting channel: ${t.message}")
        }
    }

    fun playPauseRemote() {
        try {
            val rokuIp = currentRokuIp
            if (rokuIp != null) {
                scope.launch {
                    rokuManager.sendRokuKey(rokuIp, "Play")
                }
                return
            }
            val remoteClient = currentSession?.remoteMediaClient ?: return
            if (remoteClient.isPlaying) {
                remoteClient.pause()
            } else {
                remoteClient.play()
            }
        } catch (t: Throwable) {
            Log.e("CastManager", "Error play/pause remote: ${t.message}")
        }
    }

    fun stopRemote() {
        try {
            val rokuIp = currentRokuIp
            if (rokuIp != null) {
                scope.launch {
                    rokuManager.sendRokuKey(rokuIp, "Back")
                }
                _castState.value = _castState.value.copy(currentChannel = null)
                return
            }
            currentSession?.remoteMediaClient?.stop()
            _castState.value = _castState.value.copy(currentChannel = null)
        } catch (t: Throwable) {
            Log.e("CastManager", "Error stopping remote: ${t.message}")
        }
    }

    fun disconnect() {
        try {
            currentRokuIp = null
            castContext?.sessionManager?.endCurrentSession(true)
            mediaRouter.unselect(MediaRouter.UNSELECT_REASON_DISCONNECTED)
            _castState.value = CastState(isConnected = false, isConnecting = false, deviceName = null, currentChannel = null)
            updateDiscoveredRoutes()
        } catch (t: Throwable) {
            Log.e("CastManager", "Error disconnecting session: ${t.message}")
        }
    }

    fun getCurrentRokuIp(): String? = currentRokuIp

    fun openRokuChannelOnTv(channelId: String) {
        val ip = currentRokuIp ?: return
        scope.launch {
            rokuManager.openRokuChannelOnTv(ip, channelId)
        }
    }

    fun sendRokuKeyToTv(key: String) {
        val ip = currentRokuIp ?: return
        scope.launch {
            rokuManager.sendRokuKey(ip, key)
        }
    }

    fun onDestroy() {
        try {
            scanJob?.cancel()
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
            mediaRouter.removeCallback(mediaRouterCallback)
            castContext?.sessionManager?.removeSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java
            )
        } catch (t: Throwable) {
            // Ignore
        }
    }
}
