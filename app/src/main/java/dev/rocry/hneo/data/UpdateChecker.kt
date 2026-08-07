package dev.rocry.hneo.data

import kotlinx.coroutines.flow.first

/** Decides whether the launch-time update check is due, and remembers that it ran. */
class UpdateChecker(
    private val settings: SettingsStore,
    private val updateService: UpdateService,
    private val currentVersionCode: Int,
) {
    suspend fun checkIfNeeded(): ReleaseInfo? {
        val current = settings.settings.first()
        if (!current.autoUpdateEnabled) return null

        val intervalMs = current.updateCheckIntervalHours * MILLIS_PER_HOUR
        val now = System.currentTimeMillis()
        if (now - current.lastUpdateCheck < intervalMs) return null

        settings.update { it.copy(lastUpdateCheck = now) }

        return try {
            updateService.fetchLatestRelease().takeIf { it.buildNumber > currentVersionCode }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val MILLIS_PER_HOUR = 60 * 60 * 1000L
    }
}
