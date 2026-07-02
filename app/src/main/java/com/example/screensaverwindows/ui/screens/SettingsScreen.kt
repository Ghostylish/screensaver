package com.example.screensaverwindows.ui.screens

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.screensaverwindows.renderer.BeziersRenderer
import com.example.screensaverwindows.renderer.BubblesRenderer
import com.example.screensaverwindows.renderer.FlyingWindowsRenderer
import com.example.screensaverwindows.renderer.MarqueeRenderer
import com.example.screensaverwindows.renderer.MazeRenderer
import com.example.screensaverwindows.renderer.MystifyRenderer
import com.example.screensaverwindows.renderer.PipesRenderer
import com.example.screensaverwindows.renderer.RibbonsRenderer
import com.example.screensaverwindows.renderer.StarfieldRenderer
import com.example.screensaverwindows.settings.ScreensaverEffect
import com.example.screensaverwindows.settings.ScreensaverSettings
import com.example.screensaverwindows.settings.SettingsStorage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val storage = remember(context) { SettingsStorage(context) }
    var settings by remember {
        mutableStateOf(ScreensaverSettings(effect = storage.getEffect()))
    }
    var marqueeText by remember {
        mutableStateOf(TextFieldValue(storage.getMarqueeText()))
    }
    var showMarqueeDialog by remember {
        mutableStateOf(false)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07090F))
                .padding(horizontal = 48.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(
                modifier = Modifier.weight(0.95f),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "Classic Screensavers",
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Windows 95/98/XP inspired screensaver collection for Android TV.",
                    color = Color(0xFFB7C0D8),
                    style = MaterialTheme.typography.bodyLarge,
                )
                LiveEffectPreview(settings.effect, marqueeText.text)
                SettingsSummary(settings)
            }

            LazyColumn(
                modifier = Modifier.weight(1.05f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionTitle("Screensaver")
                }
                ScreensaverEffect.entries.groupBy { it.windowsFamily }.forEach { (windowsFamily, effects) ->
                    item {
                        FamilyHeader(windowsFamily)
                    }
                    items(effects) { effect ->
                        val available = effect == ScreensaverEffect.Pipes ||
                            effect == ScreensaverEffect.Mystify ||
                            effect == ScreensaverEffect.Beziers ||
                            effect == ScreensaverEffect.Maze ||
                            effect == ScreensaverEffect.Starfield ||
                            effect == ScreensaverEffect.FlyingWindows ||
                            effect == ScreensaverEffect.Marquee ||
                            effect == ScreensaverEffect.Bubbles ||
                            effect == ScreensaverEffect.Ribbons
                        OptionCard(
                            title = effect.title,
                            subtitle = when {
                                effect == settings.effect -> "Active"
                                available -> "Ready"
                                else -> "Coming next"
                            },
                            selected = effect == settings.effect,
                            enabled = available,
                            onClick = {
                                storage.setEffect(effect)
                                settings = settings.copy(effect = effect)
                                if (effect == ScreensaverEffect.Marquee) {
                                    marqueeText = TextFieldValue(storage.getMarqueeText())
                                    showMarqueeDialog = true
                                }
                            },
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionTitle("Settings")
                }
                item { OptionCard("Speed", "${(settings.speed * 100).toInt()}%", false) }
                item { OptionCard("Color scheme", settings.colorScheme.title, false) }
                item { OptionCard("Brightness", "${(settings.brightness * 100).toInt()}%", false) }
                item { OptionCard("FPS", "${settings.targetFps}", false) }
                item { OptionCard("Render scale", "${(settings.renderScale * 100).toInt()}%", false) }
                item { OptionCard("Clock", if (settings.showClock) "On" else "Off", false) }
                item { OptionCard("Weather", if (settings.showWeather) "On" else "Off", false) }
                item { OptionCard("Photos", if (settings.showPhotos) "On" else "Off", false) }
            }
        }
    }

    if (showMarqueeDialog) {
        MarqueeTextDialog(
            value = marqueeText,
            onValueChange = { value ->
                marqueeText = if (value.text.length > SettingsStorage.MAX_MARQUEE_TEXT_LENGTH) {
                    value.copy(text = value.text.take(SettingsStorage.MAX_MARQUEE_TEXT_LENGTH))
                } else {
                    value
                }
            },
            onDismiss = {
                val finalText = marqueeText.text.ifBlank { SettingsStorage.DEFAULT_MARQUEE_TEXT }
                marqueeText = TextFieldValue(finalText)
                storage.setMarqueeText(finalText)
                showMarqueeDialog = false
            },
        )
    }
}

@Composable
private fun LiveEffectPreview(effect: ScreensaverEffect, marqueeText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF08111F), Color(0xFF111827), Color(0xFF20112A)),
                ),
            )
            .padding(24.dp),
    ) {
        key(effect, marqueeText) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { context ->
                    GLSurfaceView(context).apply {
                        setEGLContextClientVersion(3)
                        setRenderer(createPreviewRenderer(context, effect, marqueeText))
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                },
            )
        }
    }
}

private fun createPreviewRenderer(
    context: android.content.Context,
    effect: ScreensaverEffect,
    marqueeText: String,
): GLSurfaceView.Renderer =
    when (effect) {
        ScreensaverEffect.Beziers -> BeziersRenderer()
        ScreensaverEffect.Bubbles -> BubblesRenderer()
        ScreensaverEffect.FlyingWindows -> FlyingWindowsRenderer(context)
        ScreensaverEffect.Marquee -> MarqueeRenderer(
            context = context,
            previewText = marqueeText.ifBlank { SettingsStorage.DEFAULT_MARQUEE_TEXT },
        )
        ScreensaverEffect.Maze -> MazeRenderer()
        ScreensaverEffect.Mystify -> MystifyRenderer()
        ScreensaverEffect.Ribbons -> RibbonsRenderer()
        ScreensaverEffect.Starfield -> StarfieldRenderer()
        else -> PipesRenderer()
    }

@Composable
private fun PreviewPipe(color: Color, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 112.dp, height = 18.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.9f)),
        )
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 76.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.75f)),
        )
    }
}

@Composable
private fun PreviewMystifyLine(color: Color, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .size(width = if (index % 2 == 0) 64.dp else 38.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 1f - index * 0.16f)),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun PreviewStar(color: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(color),
    )
}

@Composable
private fun SettingsSummary(settings: ScreensaverSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Selected: ${settings.effect.title}", color = Color.White, fontWeight = FontWeight.SemiBold)
        Text("Available now: 3D Pipes, Mystify, Beziers, 3D Maze, Starfield, Flying Windows, Marquee, Bubbles, and Ribbons.", color = Color(0xFF9AA6BF))
    }
}

@Composable
private fun FamilyHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF62F2FF),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFFB7C0D8),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Card(
        onClick = {
            if (enabled) {
                onClick()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = when {
                selected -> Color(0xFF143B4A)
                enabled -> Color(0xFF111827)
                else -> Color(0xFF0B1018)
            },
            focusedContainerColor = if (enabled) Color(0xFF1B4D5F) else Color(0xFF0B1018),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            selected -> Color(0xFF62F2FF)
                            enabled -> Color(0xFF526071)
                            else -> Color(0xFF27303D)
                        },
                    ),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    color = if (enabled) Color.White else Color(0xFF7E8798),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    color = if (enabled) Color(0xFFB7C0D8) else Color(0xFF687284),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MarqueeTextDialog(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF111827))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Marquee text", color = Color.White, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF07090F))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { innerTextField ->
                    if (value.text.isEmpty()) {
                        Text(SettingsStorage.DEFAULT_MARQUEE_TEXT, color = Color(0xFF687284))
                    }
                    innerTextField()
                },
            )
            Text(
                "${value.text.length}/${SettingsStorage.MAX_MARQUEE_TEXT_LENGTH}",
                color = Color(0xFF9AA6BF),
                style = MaterialTheme.typography.bodySmall,
            )
            OptionCard(
                title = "Apply",
                subtitle = if (value.text.isBlank()) "Default text will be used" else value.text,
                selected = true,
                onClick = onDismiss,
            )
        }
    }
}
