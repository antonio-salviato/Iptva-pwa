package com.example.iptv.cast

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class RokuDevice(
    val ip: String,
    val friendlyName: String,
    val modelName: String = "Roku TV / Device"
)

data class RokuApp(
    val id: String,
    val name: String
)

enum class RokuPlayResult {
    SUCCESS,
    NEEDS_APP_INSTALL,
    FAILED
}

class RokuDeviceManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val discoveredRokuMap = ConcurrentHashMap<String, RokuDevice>()

    fun getDiscoveredDevices(): List<RokuDevice> = discoveredRokuMap.values.toList().sortedBy { it.friendlyName }

    suspend fun discoverRokuDevices(): List<RokuDevice> = withContext(Dispatchers.IO) {
        val ssdpQuery = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: roku:ecp\r\n\r\n"

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 2500

            val sendData = ssdpQuery.toByteArray()
            val broadcastAddress = getBroadcastAddress() ?: InetAddress.getByName("239.255.255.250")

            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, 1900)
            socket.send(sendPacket)

            val receiveData = ByteArray(2048)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 3000) {
                try {
                    val receivePacket = DatagramPacket(receiveData, receiveData.size)
                    socket.receive(receivePacket)

                    val response = String(receivePacket.data, 0, receivePacket.length)
                    if (response.contains("roku:ecp", ignoreCase = true) || response.contains("Roku", ignoreCase = true)) {
                        val deviceIp = receivePacket.address.hostAddress
                        if (deviceIp != null) {
                            fetchRokuDeviceInfo(deviceIp)?.let { rokuDevice ->
                                discoveredRokuMap[rokuDevice.ip] = rokuDevice
                            }
                        }
                    }
                } catch (t: Throwable) {
                    // Socket timeout or finish receiving
                    break
                }
            }
        } catch (e: Exception) {
            Log.w("RokuDeviceManager", "SSDP discovery error: ${e.message}")
        } finally {
            socket?.close()
        }

        // Subnet ping fallback if SSDP broadcast didn't find any or to double check
        val currentSubnet = getSubnetPrefix()
        if (currentSubnet != null && discoveredRokuMap.isEmpty()) {
            quickSubnetScan(currentSubnet)
        }

        discoveredRokuMap.values.toList()
    }

    suspend fun addManualRokuIp(ip: String): RokuDevice? = withContext(Dispatchers.IO) {
        val device = fetchRokuDeviceInfo(ip.trim())
        if (device != null) {
            discoveredRokuMap[device.ip] = device
        }
        device
    }

    suspend fun getInstalledApps(rokuIp: String): List<RokuApp> = withContext(Dispatchers.IO) {
        try {
            val url = "http://$rokuIp:8060/query/apps"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val xml = response.body?.string() ?: ""
                parseAppsXml(xml)
            } else emptyList()
        } catch (e: Exception) {
            Log.w("RokuDeviceManager", "Error querying installed apps: ${e.message}")
            emptyList()
        }
    }

    suspend fun sendRokuKey(rokuIp: String, key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val keyUrl = "http://$rokuIp:8060/keypress/$key"
            val request = Request.Builder().url(keyUrl).post("".toRequestBody()).build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun openRokuChannelStore(rokuIp: String, targetChannelId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // App ID 11 is the official Roku Channel Store
            val storeUrl = "http://$rokuIp:8060/launch/11?contentId=$targetChannelId"
            val request = Request.Builder().url(storeUrl).post("".toRequestBody()).build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun openRokuChannelOnTv(rokuIp: String, channelId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val apps = getInstalledApps(rokuIp)
            val isInstalled = apps.any { it.id == channelId }
            val targetUrl = if (isInstalled) {
                "http://$rokuIp:8060/launch/$channelId"
            } else {
                // Open Channel Store page to install
                "http://$rokuIp:8060/launch/11?contentId=$channelId"
            }
            val request = Request.Builder().url(targetUrl).post("".toRequestBody()).build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun playStreamOnRoku(rokuIp: String, streamUrl: String, title: String): RokuPlayResult = withContext(Dispatchers.IO) {
        try {
            val encodedUrl = java.net.URLEncoder.encode(streamUrl, "UTF-8")
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val isHls = streamUrl.contains(".m3u8", ignoreCase = true)
            val format = if (isHls) "hls" else "mp4"

            val apps = getInstalledApps(rokuIp)
            val hasRmp = apps.any { it.id == "15985" || it.name.contains("Media Player", ignoreCase = true) }

            Log.d("RokuDeviceManager", "Detected apps on Roku $rokuIp: ${apps.map { "${it.name}(${it.id})" }}")

            // Launch Play on Roku / Roku Media Player (Channel ID 15985) or standard ECP input
            val launchUrl = "http://$rokuIp:8060/launch/15985?contentId=$encodedUrl&mediaType=live&format=$format&u=$encodedUrl&videoFormat=$format&title=$encodedTitle"
            httpClient.newCall(Request.Builder().url(launchUrl).post("".toRequestBody()).build()).execute()

            delay(500)
            val inputUrl = "http://$rokuIp:8060/input/15985?t=v&u=$encodedUrl&videoName=$encodedTitle&videoFormat=$format"
            httpClient.newCall(Request.Builder().url(inputUrl).post("".toRequestBody()).build()).execute()

            val generalInputUrl = "http://$rokuIp:8060/input?t=v&u=$encodedUrl&videoName=$encodedTitle&videoFormat=$format"
            httpClient.newCall(Request.Builder().url(generalInputUrl).post("".toRequestBody()).build()).execute()

            delay(300)
            sendRokuKey(rokuIp, "Play")
            return@withContext RokuPlayResult.SUCCESS
        } catch (e: Exception) {
            Log.e("RokuDeviceManager", "Error playing stream on Roku $rokuIp: ${e.message}")
            RokuPlayResult.FAILED
        }
    }

    private fun parseAppsXml(xml: String): List<RokuApp> {
        val appRegex = """<app\s+id="([^"]+)"[^>]*>([^<]+)</app>""".toRegex()
        return appRegex.findAll(xml).map {
            RokuApp(id = it.groupValues[1], name = it.groupValues[2].trim())
        }.toList()
    }

    private fun fetchRokuDeviceInfo(ip: String): RokuDevice? {
        return try {
            val url = "http://$ip:8060/query/device-info"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val xml = response.body?.string() ?: ""
                val friendlyName = parseXmlValue(xml, "user-device-name")
                    .ifBlank { parseXmlValue(xml, "friendly-device-name") }
                    .ifBlank { parseXmlValue(xml, "model-name") }
                    .ifBlank { "Roku Device ($ip)" }

                val modelName = parseXmlValue(xml, "model-name").ifBlank { "Roku TV" }

                RokuDevice(
                    ip = ip,
                    friendlyName = friendlyName,
                    modelName = modelName
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseXmlValue(xml: String, tag: String): String {
        return try {
            val startTag = "<$tag>"
            val endTag = "</$tag>"
            val start = xml.indexOf(startTag)
            if (start != -1) {
                val end = xml.indexOf(endTag, start)
                if (end != -1) {
                    return xml.substring(start + startTag.length, end).trim()
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getBroadcastAddress(): InetAddress? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp: DhcpInfo = wifiManager?.dhcpInfo ?: return null
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            val quads = ByteArray(4)
            for (k in 0..3) {
                quads[k] = (broadcast shr (k * 8) and 0xFF).toByte()
            }
            InetAddress.getByAddress(quads)
        } catch (e: Exception) {
            null
        }
    }

    private fun getSubnetPrefix(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipAddress = wifiManager?.dhcpInfo?.ipAddress ?: return null
            if (ipAddress == 0) return null
            val ipStr = String.format(
                "%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff
            )
            ipStr
        } catch (e: Exception) {
            null
        }
    }

    private fun quickSubnetScan(subnetPrefix: String) {
        val commonIps = listOf(203, 100, 101, 102, 105, 110, 150, 200, 201, 202, 204, 205, 210)
        for (host in commonIps) {
            val testIp = "$subnetPrefix.$host"
            fetchRokuDeviceInfo(testIp)?.let { roku ->
                discoveredRokuMap[roku.ip] = roku
            }
        }
    }
}
