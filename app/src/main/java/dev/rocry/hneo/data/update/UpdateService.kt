package dev.rocry.hneo.data.update

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
import kotlinx.serialization.json.Json
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

/** Why an update could not be fetched, in terms a user can act on. */
sealed class UpdateFailure(message: String) : Exception(message) {
    /** GitHub answered, but said no — rate limits, missing releases, outages. */
    class Rejected(val code: Int, message: String) : UpdateFailure(message)

    /** GitHub answered with something we cannot read. */
    class Unreadable(message: String) : UpdateFailure(message)

    /** We never reached GitHub. */
    class Unreachable(message: String) : UpdateFailure(message)
}

/** Reads GitHub releases and fetches APKs. When to check is [AppUpdater]'s decision. */
class UpdateService(
    private val http: JsonHttp,
    private val engine: HttpEngine,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /** The one place that knows what a release APK is called. */
    fun apkFileName(versionName: String): String = "hneo-$versionName.apk"

    suspend fun fetchLatestRelease(): ReleaseInfo {
        val release = try {
            http.decodeObject(
                HttpRequest(RELEASES_URL, headers = mapOf("Accept" to "application/vnd.github+json")),
            )
        } catch (e: HttpFailure) {
            throw e.asUpdateFailure()
        }

        val tagName = release["tag_name"]?.jsonPrimitive?.contentOrNull
            ?: throw UpdateFailure.Unreadable("GitHub returned a release with no tag")
        val buildNumber = tagName.removePrefix(TAG_PREFIX).toIntOrNull()
            ?: throw UpdateFailure.Unreadable("Unrecognised release tag '$tagName'")
        val downloadUrl = release["assets"]?.jsonArray
            ?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
            ?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
            ?: throw UpdateFailure.Unreadable("Release $tagName has no APK attached")

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
        val response = try {
            engine.execute(HttpRequest(url, readTimeoutSeconds = DOWNLOAD_TIMEOUT_SECONDS))
        } catch (e: HttpFailure) {
            throw e.asUpdateFailure()
        }

        response.use {
            val stream = it.bodyStream()
            if (!it.isSuccessful) {
                throw UpdateFailure.Rejected(it.code, "Download failed with HTTP ${it.code}")
            }
            val total = it.contentLength
            destination.parentFile?.mkdirs()
            stream.use { input ->
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

    /**
     * GitHub explains itself in a `message` field. Without this, a rate-limit 403
     * body reaches the release parser and is reported as a missing tag.
     */
    private fun HttpFailure.asUpdateFailure(): UpdateFailure = when (this) {
        is HttpFailure.Status -> UpdateFailure.Rejected(code, gitHubMessage(body) ?: "GitHub returned HTTP $code")
        is HttpFailure.Malformed -> UpdateFailure.Unreadable("GitHub returned an unexpected response")
        is HttpFailure.Transport -> UpdateFailure.Unreachable("Could not reach GitHub: ${message.orEmpty()}")
    }

    private fun gitHubMessage(body: String): String? = try {
        json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/rocrp/hneo-android/releases/latest"
        const val TAG_PREFIX = "build-"
        const val DOWNLOAD_TIMEOUT_SECONDS = 60L
        const val BUFFER_BYTES = 8192
    }
}
