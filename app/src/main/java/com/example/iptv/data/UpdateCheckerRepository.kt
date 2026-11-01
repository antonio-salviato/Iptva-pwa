package com.example.iptv.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.iptv.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UpdateCheckerRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val repoOwner = "antonio-salviato"
    private val repoName = "Iptv"

    suspend fun checkForUpdates(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val candidateUrls = listOf(
                "https://raw.githubusercontent.com/$repoOwner/$repoName/main/version.json?t=$timestamp",
                "https://raw.githubusercontent.com/$repoOwner/$repoName/master/version.json?t=$timestamp"
            )

            // 1. Try raw version.json from main and master branches
            for (rawUrl in candidateUrls) {
                try {
                    val request = Request.Builder()
                        .url(rawUrl)
                        .header("User-Agent", "Mozilla/5.0 (Android) IPTV-Cast-Updater")
                        .header("Cache-Control", "no-cache")
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val jsonString = response.body?.string() ?: ""
                        if (jsonString.isNotBlank()) {
                            val json = JSONObject(jsonString)
                            val serverVersionCode = json.optInt("versionCode", 1)
                            val serverVersionName = json.optString("versionName", "1.0.0")
                            val releaseNotes = json.optString("releaseNotes", "Nova versão com melhorias e correções.")
                            val apkUrl = json.optString("downloadUrl", "https://github.com/$repoOwner/$repoName/releases/latest")

                            val currentVersionCode = BuildConfig.VERSION_CODE
                            val currentVersionName = BuildConfig.VERSION_NAME

                            val isAvailable = (serverVersionCode > currentVersionCode) ||
                                              (isNewerVersionName(serverVersionName, currentVersionName))

                            Log.d("UpdateChecker", "Server: v$serverVersionName ($serverVersionCode), Current: v$currentVersionName ($currentVersionCode), UpdateAvailable: $isAvailable")

                            return@withContext Result.success(
                                AppUpdateInfo(
                                    versionCode = serverVersionCode,
                                    versionName = serverVersionName,
                                    releaseNotes = releaseNotes,
                                    apkUrl = apkUrl,
                                    isUpdateAvailable = isAvailable
                                )
                            )
                        }
                    }
                } catch (t: Throwable) {
                    Log.w("UpdateChecker", "Failed checking $rawUrl: ${t.message}")
                }
            }

            // 2. Fallback attempt: GitHub Releases API
            val fallbackRequest = Request.Builder()
                .url("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
                .header("User-Agent", "Mozilla/5.0 (Android) IPTV-Cast-Updater")
                .header("Accept", "application/vnd.github+json")
                .header("Cache-Control", "no-cache")
                .build()

            val fallbackResponse = client.newCall(fallbackRequest).execute()
            if (fallbackResponse.isSuccessful) {
                val jsonString = fallbackResponse.body?.string() ?: ""
                if (jsonString.isNotBlank()) {
                    val json = JSONObject(jsonString)
                    val tagName = json.optString("tag_name", "v1.0.0").replace("v", "").trim()
                    val body = json.optString("body", "Nova versão lançada no GitHub!")

                    val assets = json.optJSONArray("assets")
                    var downloadUrl = json.optString("html_url", "https://github.com/$repoOwner/$repoName/releases")

                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", downloadUrl)
                                break
                            }
                        }
                    }

                    val currentVersionName = BuildConfig.VERSION_NAME
                    val currentVersionCode = BuildConfig.VERSION_CODE
                    val isAvailable = isNewerVersionName(tagName, currentVersionName)

                    return@withContext Result.success(
                        AppUpdateInfo(
                            versionCode = currentVersionCode + 1,
                            versionName = tagName,
                            releaseNotes = body,
                            apkUrl = downloadUrl,
                            isUpdateAvailable = isAvailable
                        )
                    )
                }
            } else if (fallbackResponse.code == 404) {
                return@withContext Result.failure(
                    Exception("O repositório '$repoOwner/$repoName' retornou 404 no GitHub. Se o seu repositório for PRIVADO, torne-o PÚBLICO (Public) nas configurações do GitHub para permitir downloads do APK e verificação de atualizações.")
                )
            }

            Result.failure(Exception("Não foi possível conectar ao servidor GitHub. Código: ${fallbackResponse.code}"))
        } catch (e: Exception) {
            Log.e("UpdateCheckerRepository", "Error checking for updates", e)
            Result.failure(e)
        }
    }

    private fun isNewerVersionName(server: String, current: String): Boolean {
        return try {
            val sParts = server.trimStart('v', 'V').split(".").mapNotNull { it.toIntOrNull() }
            val cParts = current.trimStart('v', 'V').split(".").mapNotNull { it.toIntOrNull() }
            for (i in 0 until maxOf(sParts.size, cParts.size)) {
                val s = sParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (s > c) return true
                if (s < c) return false
            }
            false
        } catch (e: Exception) {
            server.trimStart('v', 'V') != current.trimStart('v', 'V')
        }
    }
}
