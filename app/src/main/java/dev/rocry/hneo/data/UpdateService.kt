package dev.rocry.hneo.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object UpdateService {
    private const val RELEASES_URL =
        "https://api.github.com/repos/rocrp/hneo-android/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    data class ReleaseInfo(
        val tagName: String,
        val versionName: String,
        val changelog: String,
        val downloadUrl: String,
        val buildNumber: Int,
    )

    suspend fun checkForUpdate(currentVersionCode: Int): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        val release = json.parseToJsonElement(body).jsonObject

        val tagName = release["tag_name"]?.jsonPrimitive?.contentOrNull
            ?: throw Exception("Missing tag_name")
        val buildNumber = tagName.removePrefix("build-").toIntOrNull()
            ?: throw Exception("Invalid tag format: $tagName")

        if (buildNumber <= currentVersionCode) return@withContext null

        val downloadUrl = release["assets"]?.jsonArray
            ?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
            ?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
            ?: throw Exception("No APK asset found")

        ReleaseInfo(
            tagName = tagName,
            versionName = release["name"]?.jsonPrimitive?.contentOrNull ?: tagName,
            changelog = release["body"]?.jsonPrimitive?.contentOrNull ?: "",
            downloadUrl = downloadUrl,
            buildNumber = buildNumber,
        )
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val responseBody = response.body ?: throw Exception("Empty download response")
        val contentLength = responseBody.contentLength()

        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, fileName)

        responseBody.byteStream().use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        onProgress(bytesRead.toFloat() / contentLength)
                    }
                }
            }
        }
        file
    }
}
