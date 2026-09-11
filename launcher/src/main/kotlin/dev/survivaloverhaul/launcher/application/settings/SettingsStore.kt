package dev.survivaloverhaul.launcher.application.settings

import dev.survivaloverhaul.launcher.domain.settings.LauncherSettings

/** Persistence for user settings. Implemented against a JSON file. */
interface SettingsStore {
    fun load(): LauncherSettings

    fun save(settings: LauncherSettings)
}
