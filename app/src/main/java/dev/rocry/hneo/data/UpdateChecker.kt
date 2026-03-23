package dev.rocry.hneo.data

import android.content.Context
import kotlinx.coroutines.flow.first

object UpdateChecker {
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

    suspend fun checkIfNeeded(context: Context, currentVersionCode: Int): UpdateService.ReleaseInfo? {
        val settings = settingsFlow(context).first()
        if (!settings.autoUpdateEnabled) return null

        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck < CHECK_INTERVAL_MS) return null

        updateSetting(context, SettingsKeys.LAST_UPDATE_CHECK, now)

        return try {
            UpdateService.checkForUpdate(currentVersionCode)
        } catch (_: Exception) {
            null
        }
    }
}
