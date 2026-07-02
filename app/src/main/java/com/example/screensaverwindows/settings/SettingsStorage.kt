package com.example.screensaverwindows.settings

import android.content.Context

class SettingsStorage(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getEffect(): ScreensaverEffect {
        val storedName = preferences.getString(KEY_EFFECT, ScreensaverEffect.Pipes.name)
        return ScreensaverEffect.entries.firstOrNull { it.name == storedName } ?: ScreensaverEffect.Pipes
    }

    fun setEffect(effect: ScreensaverEffect) {
        preferences.edit().putString(KEY_EFFECT, effect.name).apply()
    }

    fun getMarqueeText(): String =
        preferences.getString(KEY_MARQUEE_TEXT, DEFAULT_MARQUEE_TEXT)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_MARQUEE_TEXT

    fun setMarqueeText(text: String) {
        preferences.edit().putString(KEY_MARQUEE_TEXT, text.take(MAX_MARQUEE_TEXT_LENGTH)).apply()
    }

    companion object {
        const val DEFAULT_MARQUEE_TEXT = "[Windows NT 3.1]"
        const val MAX_MARQUEE_TEXT_LENGTH = 80

        private const val PREFERENCES_NAME = "classic_screensaver_settings"
        private const val KEY_EFFECT = "effect"
        private const val KEY_MARQUEE_TEXT = "marquee_text"
    }
}
