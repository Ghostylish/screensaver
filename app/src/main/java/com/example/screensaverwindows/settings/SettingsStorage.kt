package com.example.screensaverwindows.settings

import android.content.Context

class SettingsStorage(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getEffect(): ScreensaverEffect {
        val storedName = preferences.getString(KEY_EFFECT, ScreensaverEffect.Pipes.name)
        val effect = ScreensaverEffect.entries.firstOrNull { it.name == storedName } ?: ScreensaverEffect.Pipes
        return if (effect in DISABLED_EFFECTS) ScreensaverEffect.Pipes else effect
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

    fun getThreeDText(): String =
        preferences.getString(KEY_THREE_D_TEXT, DEFAULT_THREE_D_TEXT)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_THREE_D_TEXT

    fun setThreeDText(text: String) {
        preferences.edit().putString(KEY_THREE_D_TEXT, text.take(MAX_THREE_D_TEXT_LENGTH)).apply()
    }

    fun getWeatherCity(): String =
        preferences.getString(KEY_WEATHER_CITY, DEFAULT_WEATHER_CITY)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_WEATHER_CITY

    fun setWeatherCity(city: String) {
        val normalizedCity = city.trim().ifBlank { DEFAULT_WEATHER_CITY }.take(MAX_WEATHER_CITY_LENGTH)
        preferences.edit()
            .putString(KEY_WEATHER_CITY, normalizedCity)
            .putBoolean(KEY_WEATHER_LOCATION_RESOLVED, false)
            .apply()
    }

    fun getWeatherLatitude(): Float =
        preferences.getFloat(KEY_WEATHER_LATITUDE, DEFAULT_WEATHER_LATITUDE)

    fun getWeatherLongitude(): Float =
        preferences.getFloat(KEY_WEATHER_LONGITUDE, DEFAULT_WEATHER_LONGITUDE)

    fun getWeatherCountry(): String =
        preferences.getString(KEY_WEATHER_COUNTRY, DEFAULT_WEATHER_COUNTRY)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_WEATHER_COUNTRY

    fun hasResolvedWeatherLocation(): Boolean =
        preferences.getBoolean(KEY_WEATHER_LOCATION_RESOLVED, false)

    fun setWeatherLocation(city: String, country: String, latitude: Float, longitude: Float) {
        preferences.edit()
            .putString(KEY_WEATHER_CITY, city.trim().ifBlank { DEFAULT_WEATHER_CITY }.take(MAX_WEATHER_CITY_LENGTH))
            .putString(KEY_WEATHER_COUNTRY, country.trim().ifBlank { DEFAULT_WEATHER_COUNTRY }.take(MAX_WEATHER_CITY_LENGTH))
            .putFloat(KEY_WEATHER_LATITUDE, latitude)
            .putFloat(KEY_WEATHER_LONGITUDE, longitude)
            .putBoolean(KEY_WEATHER_LOCATION_RESOLVED, true)
            .apply()
    }

    fun getSettings(): ScreensaverSettings =
        ScreensaverSettings(
            effect = getEffect(),
            speed = preferences.getFloat(KEY_SPEED, 1f),
            brightness = preferences.getFloat(KEY_BRIGHTNESS, 1f),
            showClock = preferences.getBoolean(KEY_SHOW_CLOCK, true),
            showWeather = preferences.getBoolean(KEY_SHOW_WEATHER, true),
        )

    fun setSpeed(speed: Float) {
        preferences.edit().putFloat(KEY_SPEED, speed.coerceIn(0.5f, 1.5f)).apply()
    }

    fun setBrightness(brightness: Float) {
        preferences.edit().putFloat(KEY_BRIGHTNESS, brightness.coerceIn(0.45f, 1f)).apply()
    }

    fun setShowClock(showClock: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_CLOCK, showClock).apply()
    }

    fun setShowWeather(showWeather: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_WEATHER, showWeather).apply()
    }

    companion object {
        private val DISABLED_EFFECTS = setOf(ScreensaverEffect.Photos)

        const val DEFAULT_MARQUEE_TEXT = "[Windows NT 3.1]"
        const val MAX_MARQUEE_TEXT_LENGTH = 80
        const val DEFAULT_THREE_D_TEXT = "Windows Vista"
        const val MAX_THREE_D_TEXT_LENGTH = 24
        const val DEFAULT_WEATHER_CITY = "London"
        const val DEFAULT_WEATHER_COUNTRY = "United Kingdom"
        const val MAX_WEATHER_CITY_LENGTH = 60

        private const val PREFERENCES_NAME = "classic_screensaver_settings"
        private const val KEY_EFFECT = "effect"
        private const val KEY_MARQUEE_TEXT = "marquee_text"
        private const val KEY_THREE_D_TEXT = "three_d_text"
        private const val KEY_SPEED = "speed"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_SHOW_CLOCK = "show_clock"
        private const val KEY_SHOW_WEATHER = "show_weather"
        private const val KEY_WEATHER_CITY = "weather_city"
        private const val KEY_WEATHER_COUNTRY = "weather_country"
        private const val KEY_WEATHER_LATITUDE = "weather_latitude"
        private const val KEY_WEATHER_LONGITUDE = "weather_longitude"
        private const val KEY_WEATHER_LOCATION_RESOLVED = "weather_location_resolved"
        private const val DEFAULT_WEATHER_LATITUDE = 51.5072f
        private const val DEFAULT_WEATHER_LONGITUDE = -0.1276f
    }
}
