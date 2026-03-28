package dev.rocry.hneo.data

import android.content.Context
import kotlinx.coroutines.flow.first

object UpdateChecker {
    suspend fun checkIfNeeded(context: Context, currentVersionCode: Int): UpdateService.ReleaseInfo? {
        val settings = settingsFlow(context).first()
        if (!settings.autoUpdateEnabled) return null

        val intervalMs = settings.updateCheckIntervalHours * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck < intervalMs) return null

        updateSetting(context, SettingsKeys.LAST_UPDATE_CHECK, now)

        return try {
            UpdateService.checkForUpdate(currentVersionCode)
        } catch (_: Exception) {
            null
        }
    }
}
