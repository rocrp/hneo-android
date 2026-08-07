package dev.rocry.hneo.data.update

import dev.rocry.hneo.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** Where the app is in the check → download → install lifecycle. */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    /**
     * [promptOnLaunch] is true only for an automatic check, so declining the
     * launch dialog leaves the update reachable from Settings without nagging.
     */
    data class Available(val release: ReleaseInfo, val promptOnLaunch: Boolean) : UpdateState

    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState

    data class Failed(val message: String) : UpdateState
}

/** Installs a downloaded APK. Kept behind an interface so policy stays testable. */
fun interface ApkInstaller {
    fun install(file: File)
}

/**
 * Self-update as one observable state machine: when to check, what was found,
 * how far the download got. The launch dialog and the settings section are thin
 * adapters over this — neither hand-rolls the flow, and neither can bypass the
 * interval policy.
 */
class AppUpdater(
    private val updateService: UpdateService,
    private val settings: SettingsStore,
    private val installer: ApkInstaller,
    private val currentVersionCode: Int,
    private val updatesDir: File,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    /**
     * The launch-time check. Honours the auto-update toggle and the interval, and
     * reports failures into [state] instead of discarding them.
     */
    suspend fun checkOnLaunch() {
        val current = settings.settings.first()
        if (!current.autoUpdateEnabled) return
        if (now() - current.lastUpdateCheck < current.updateCheckIntervalHours * MILLIS_PER_HOUR) return

        runCheck(promptOnLaunch = true)
    }

    /**
     * A user-initiated check. It stamps the interval like any other check —
     * otherwise declining an update here gets re-prompted on the very next launch.
     */
    suspend fun checkNow() {
        _state.value = UpdateState.Checking
        runCheck(promptOnLaunch = false)
    }

    /** Stops the launch dialog nagging without forgetting the update. */
    fun dismissLaunchPrompt() {
        val current = _state.value
        if (current is UpdateState.Available && current.promptOnLaunch) {
            _state.value = current.copy(promptOnLaunch = false)
        }
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }

    fun download(release: ReleaseInfo) {
        downloadJob?.cancel()
        _state.value = UpdateState.Downloading(release, 0f)

        downloadJob = scope.launch {
            try {
                val file = updateService.downloadApk(
                    url = release.downloadUrl,
                    destination = File(updatesDir, updateService.apkFileName(release.versionName)),
                    onProgress = { _state.value = UpdateState.Downloading(release, it) },
                )
                installer.install(file)
                _state.value = UpdateState.Idle
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(e.readableMessage())
            }
        }
    }

    private suspend fun runCheck(promptOnLaunch: Boolean) {
        settings.update { it.copy(lastUpdateCheck = now()) }

        _state.value = try {
            val release = updateService.fetchLatestRelease()
            if (release.buildNumber > currentVersionCode) {
                UpdateState.Available(release, promptOnLaunch)
            } else {
                UpdateState.UpToDate
            }
        } catch (e: Exception) {
            UpdateState.Failed(e.readableMessage())
        }
    }

    private fun Exception.readableMessage(): String =
        (this as? UpdateFailure)?.message ?: message ?: "Update failed"

    private companion object {
        const val MILLIS_PER_HOUR = 60 * 60 * 1000L
    }
}
