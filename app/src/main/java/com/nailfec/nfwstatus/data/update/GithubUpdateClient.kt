package com.nailfec.nfwstatus.data.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class GithubUpdateClient @Inject constructor(
    @ApplicationContext private val context: Context,
    client: OkHttpClient,
    private val json: Json,
) {
    private val http = client.newBuilder()
        .callTimeout(2, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    suspend fun check(currentVersionCode: Int): UpdateCheck = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (response.code == 404) return@use UpdateCheck.NoRelease
                if (!response.isSuccessful) return@use UpdateCheck.Failed
                val body = response.body?.string().orEmpty()
                val release = runCatching { json.decodeFromString<GithubRelease>(body) }.getOrNull()
                    ?: return@use UpdateCheck.Failed
                val asset = release.assets.firstOrNull { ASSET_NAME.matches(it.name) }
                    ?: return@use UpdateCheck.NoPackage
                val versionCode = ASSET_NAME.matchEntire(asset.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: return@use UpdateCheck.NoPackage
                if (!allowedDownload(asset.browserDownloadUrl)) return@use UpdateCheck.Failed
                if (versionCode <= currentVersionCode) return@use UpdateCheck.UpToDate
                UpdateCheck.Available(
                    versionName = release.tagName.removePrefix("v").removePrefix("V").ifBlank { versionCode.toString() },
                    versionCode = versionCode,
                    downloadUrl = asset.browserDownloadUrl,
                )
            }
        } catch (_: IOException) {
            UpdateCheck.Failed
        }
    }

    suspend fun download(url: String, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        if (!allowedDownload(url)) return@withContext null
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/octet-stream")
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                val file = File(context.cacheDir, "update.apk")
                if (file.exists()) file.delete()
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var readTotal = 0L
                        var lastBucket = -1
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            readTotal += count
                            if (total > 0L) {
                                val bucket = ((readTotal * 40) / total).toInt()
                                if (bucket != lastBucket) {
                                    lastBucket = bucket
                                    onProgress(readTotal.toFloat() / total.toFloat())
                                }
                            }
                        }
                    }
                }
                if (file.length() <= 0L) {
                    file.delete()
                    null
                } else {
                    onProgress(1f)
                    file
                }
            }
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val RELEASE_URL = "https://api.github.com/repos/NailFec/nfw-status-android/releases/latest"
        const val USER_AGENT = "nfw-status-android"
        val ASSET_NAME = Regex("""nfw-status-c(\d+)\.apk""")

        fun allowedDownload(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            if (parsed.scheme != "https") return false
            val host = parsed.host.lowercase()
            return host == "github.com" || host.endsWith(".githubusercontent.com")
        }
    }
}

sealed interface UpdateCheck {
    data class Available(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
    ) : UpdateCheck

    data object UpToDate : UpdateCheck
    data object NoRelease : UpdateCheck
    data object NoPackage : UpdateCheck
    data object Failed : UpdateCheck
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
