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
    ThreeDText("3D Text", "Windows Vista"),
}

data class ScreensaverSettings(
    val effect: ScreensaverEffect = ScreensaverEffect.Pipes,
    val speed: Float = 1f,
    val brightness: Float = 1f,
    val showClock: Boolean = true,
    val showWeather: Boolean = false,
)
