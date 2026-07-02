package com.example.screensaverwindows

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.example.screensaverwindows.ui.screens.SettingsScreen
import com.example.screensaverwindows.ui.theme.ScreensaverwindowsTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreensaverwindowsTheme {
                SettingsScreen()
            }
        }
    }
}
