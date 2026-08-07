package dev.rocry.hneo.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.rocry.hneo.data.http.HttpEngine
import dev.rocry.hneo.data.http.HttpFailure
import dev.rocry.hneo.data.http.HttpRequest
import dev.rocry.hneo.data.http.JsonHttp
import dev.rocry.hneo.data.http.isSuccessful
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val buildNumber: Int,
)

/** Reads GitHub releases and fetches APKs. When to check is somebody else's decision. */
class UpdateService(
    private val http: JsonHttp,
    private val engine: HttpEngine,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun fetchLatestRelease(): ReleaseInfo {
        val release = http.decodeObject(
            HttpRequest(RELEASES_URL, headers = mapOf("Accept" to "application/vnd.github+json")),
        )

        val tagName = release["tag_name"]?.jsonPrimitive?.contentOrNull
            ?: throw HttpFailure.Malformed(IllegalStateException("release has no tag_name"))
        val buildNumber = tagName.removePrefix(TAG_PREFIX).toIntOrNull()
            ?: throw HttpFailure.Malformed(IllegalStateException("unrecognised tag '$tagName'"))
        val downloadUrl = release["assets"]?.jsonArray
            ?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
            ?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
            ?: throw HttpFailure.Malformed(IllegalStateException("release $tagName has no APK asset"))

        return ReleaseInfo(
            tagName = tagName,
            versionName = release["name"]?.jsonPrimitive?.contentOrNull ?: tagName,
            changelog = release["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            downloadUrl = downloadUrl,
            buildNumber = buildNumber,
        )
    }

    suspend fun downloadApk(
        url: String,
        destination: File,
        onProgress: (Float) -> Unit,
    ): File = withContext(ioDispatcher) {
        engine.execute(HttpRequest(url, readTimeoutSeconds = DOWNLOAD_TIMEOUT_SECONDS)).use { response ->
            if (!response.isSuccessful) {
                throw HttpFailure.Status(response.code, response.bodyStream().bufferedReader().readText())
            }
            val total = response.contentLength
            destination.parentFile?.mkdirs()
            response.bodyStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress(written.toFloat() / total)
                    }
                }
            }
        }
        destination
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/rocrp/hneo-android/releases/latest"
        const val TAG_PREFIX = "build-"
        const val DOWNLOAD_TIMEOUT_SECONDS = 60L
        const val BUFFER_BYTES = 8192
    }
}
