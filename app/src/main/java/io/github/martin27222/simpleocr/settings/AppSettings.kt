package io.github.martin27222.simpleocr.settings

import android.content.Context
import androidx.core.content.edit

/** Everything the user can change. The defaults are what the app does out of the box. */
data class AppSettings(
    /** Flatten photos of curved pages before reading them. */
    val autoDewarp: Boolean = true,
    /** Put words back together that were split with a hyphen at a line end. */
    val joinHyphenatedWords: Boolean = true,
    /** Copy the lines of a paragraph as one flowing text instead of keeping the line breaks. */
    val flowingText: Boolean = true,
    /** Copy the text to the clipboard as soon as a photo has been read. */
    val autoCopy: Boolean = false,
    /** Save every photo taken in the app to the gallery. */
    val autoSavePhotos: Boolean = false,
    /** Switch the light on when the camera opens. */
    val lightOnAtStart: Boolean = false,
)

/** Keeps the settings in a private preferences file on the device. */
class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            autoDewarp = preferences.getBoolean(AUTO_DEWARP, defaults.autoDewarp),
            joinHyphenatedWords = preferences.getBoolean(JOIN_HYPHENATED, defaults.joinHyphenatedWords),
            flowingText = preferences.getBoolean(FLOWING_TEXT, defaults.flowingText),
            autoCopy = preferences.getBoolean(AUTO_COPY, defaults.autoCopy),
            autoSavePhotos = preferences.getBoolean(AUTO_SAVE, defaults.autoSavePhotos),
            lightOnAtStart = preferences.getBoolean(LIGHT_ON, defaults.lightOnAtStart),
        )
    }

    fun save(settings: AppSettings) {
        preferences.edit {
            putBoolean(AUTO_DEWARP, settings.autoDewarp)
            putBoolean(JOIN_HYPHENATED, settings.joinHyphenatedWords)
            putBoolean(FLOWING_TEXT, settings.flowingText)
            putBoolean(AUTO_COPY, settings.autoCopy)
            putBoolean(AUTO_SAVE, settings.autoSavePhotos)
            putBoolean(LIGHT_ON, settings.lightOnAtStart)
        }
    }

    private companion object {
        const val AUTO_DEWARP = "auto_dewarp"
        const val JOIN_HYPHENATED = "join_hyphenated_words"
        const val FLOWING_TEXT = "flowing_text"
        const val AUTO_COPY = "auto_copy"
        const val AUTO_SAVE = "auto_save_photos"
        const val LIGHT_ON = "light_on_at_start"
    }
}
