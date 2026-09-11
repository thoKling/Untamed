package dev.untamed.launcher.application.settings

import dev.untamed.launcher.domain.settings.LauncherSettings

/** Persistence for user settings. Implemented against a JSON file. */
interface SettingsStore {
    fun load(): LauncherSettings

    fun save(settings: LauncherSettings)
}
