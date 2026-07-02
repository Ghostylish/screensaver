package com.example.screensaverwindows.settings

enum class ScreensaverEffect(val title: String, val windowsFamily: String) {
    Pipes("3D Pipes", "Windows XP classics"),
    Mystify("Mystify", "Windows XP classics"),
    Beziers("Beziers", "Windows XP classics"),
    Maze("3D Maze", "Windows XP classics"),
    Starfield("Starfield", "Windows XP classics"),
    FlyingWindows("Flying Windows", "Windows XP classics"),
    Marquee("Marquee", "Windows XP classics"),
    Bubbles("Bubbles", "Windows Vista"),
    Ribbons("Ribbons", "Windows 7"),
    ModernFlow("Modern Flow", "Windows 10 / 11 inspired"),
}

enum class ColorScheme(val title: String) {
    Neon("Neon"),
    Chrome("Chrome"),
    Glass("Glass"),
    Random("Random"),
}

data class ScreensaverSettings(
    val effect: ScreensaverEffect = ScreensaverEffect.Pipes,
    val speed: Float = 0.65f,
    val brightness: Float = 0.85f,
    val targetFps: Int = 60,
    val renderScale: Float = 1f,
    val colorScheme: ColorScheme = ColorScheme.Neon,
    val showClock: Boolean = true,
    val showWeather: Boolean = false,
    val showPhotos: Boolean = false,
)
