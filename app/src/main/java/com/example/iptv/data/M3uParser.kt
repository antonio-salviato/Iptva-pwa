package com.example.iptv.data

import com.example.iptv.model.Channel
import java.io.BufferedReader
import java.io.StringReader
import java.util.UUID

object M3uParser {

    private val tvgNameRegex = """tvg-name="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
    private val groupTitleRegex = """group-title="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
    private val tvgLogoRegex = """tvg-logo="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
    private val tvgIdRegex = """tvg-id="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)

    fun parse(m3uContent: String): List<Channel> {
        return parse(BufferedReader(StringReader(m3uContent)))
    }

    fun parse(reader: BufferedReader): List<Channel> {
        val channels = ArrayList<Channel>(5000)
        var line: String?

        var currentTvgName: String? = null
        var currentTvgId: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var currentTitle: String? = null

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line?.trim() ?: continue
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                // Reset metadata for next entry
                currentTvgName = tvgNameRegex.find(trimmed)?.groupValues?.get(1)
                currentTvgId = tvgIdRegex.find(trimmed)?.groupValues?.get(1)
                currentLogo = tvgLogoRegex.find(trimmed)?.groupValues?.get(1)
                currentGroup = groupTitleRegex.find(trimmed)?.groupValues?.get(1)

                // The title is the part after the last comma
                val commaIndex = trimmed.lastIndexOf(',')
                currentTitle = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else null
            } else if (!trimmed.startsWith("#")) {
                // This line is a Stream URL
                val streamUrl = trimmed
                if (streamUrl.startsWith("http://", ignoreCase = true) || 
                    streamUrl.startsWith("https://", ignoreCase = true) ||
                    streamUrl.startsWith("rtmp://", ignoreCase = true)) {
                    
                    val name = currentTvgName?.ifBlank { null }
                        ?: currentTitle?.ifBlank { null }
                        ?: extractNameFromUrl(streamUrl)
                    
                    val group = currentGroup?.ifBlank { null } ?: "Variedades"

                    val channel = Channel(
                        id = generateId(streamUrl, name),
                        name = name,
                        group = group,
                        logoUrl = currentLogo?.ifBlank { null },
                        streamUrl = streamUrl,
                        tvgId = currentTvgId
                    )
                    channels.add(channel)

                    // Clear state for next channel
                    currentTvgName = null
                    currentTvgId = null
                    currentLogo = null
                    currentGroup = null
                    currentTitle = null
                }
            }
        }
        return channels
    }

    private fun extractNameFromUrl(url: String): String {
        return try {
            val lastSegment = url.substringAfterLast('/').substringBefore('?')
            if (lastSegment.isNotEmpty()) {
                lastSegment.replace(".m3u8", "").replace(".ts", "").replace("_", " ")
            } else {
                "Canal ${UUID.randomUUID().toString().take(4)}"
            }
        } catch (e: Exception) {
            "Canal IPTV"
        }
    }

    private fun generateId(url: String, name: String): String {
        return (url.hashCode().toString() + "_" + name.hashCode().toString())
    }
}
