package com.example.screensaverwindows.ui.screens

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.unit.sp
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
import com.example.screensaverwindows.renderer.PhotosRenderer
import com.example.screensaverwindows.renderer.PipesRenderer
import com.example.screensaverwindows.renderer.RibbonsRenderer
import com.example.screensaverwindows.renderer.StarfieldRenderer
import com.example.screensaverwindows.renderer.ThreeDTextRenderer
import com.example.screensaverwindows.renderer.WindowsEnergyRenderer
import com.example.screensaverwindows.settings.RuntimeSettings
import com.example.screensaverwindows.settings.ScreensaverEffect
import com.example.screensaverwindows.settings.ScreensaverSettings
import com.example.screensaverwindows.settings.SettingsStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val storage = remember(context) { SettingsStorage(context) }
    var settings by remember {
        mutableStateOf(storage.getSettings())
    }
    var marqueeText by remember {
        mutableStateOf(TextFieldValue(storage.getMarqueeText()))
    }
    var threeDText by remember {
        mutableStateOf(TextFieldValue(storage.getThreeDText()))
    }
    var showMarqueeDialog by remember {
        mutableStateOf(false)
    }
    var showThreeDTextDialog by remember {
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
                LiveEffectPreview(settings, marqueeText.text, threeDText.text)
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
                            effect == ScreensaverEffect.ThreeDText ||
                            effect == ScreensaverEffect.WindowsEnergy ||
                            effect == ScreensaverEffect.Ribbons ||
                            effect == ScreensaverEffect.Photos
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
                                if (effect == ScreensaverEffect.ThreeDText) {
                                    threeDText = TextFieldValue(storage.getThreeDText())
                                    showThreeDTextDialog = true
                                }
                            },
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionTitle("Settings")
                }
                item {
                    OptionCard(
                        title = "Speed",
                        subtitle = "${settings.speed}x",
                        selected = false,
                        onClick = {
                            val next = nextSpeed(settings.speed)
                            storage.setSpeed(next)
                            RuntimeSettings.speed = next
                            settings = settings.copy(speed = next)
                        },
                    )
                }
                item {
                    OptionCard(
                        title = "Brightness",
                        subtitle = "${(settings.brightness * 100).toInt()}%",
                        selected = false,
                        onClick = {
                            val next = nextBrightness(settings.brightness)
                            storage.setBrightness(next)
                            settings = settings.copy(brightness = next)
                        },
                    )
                }
                item {
                    OptionCard(
                        title = "Clock",
                        subtitle = if (settings.showClock) "On" else "Off",
                        selected = settings.showClock,
                        onClick = {
                            val next = !settings.showClock
                            storage.setShowClock(next)
                            settings = settings.copy(showClock = next)
                        },
                    )
                }
                item {
                    OptionCard(
                        title = "Weather",
                        subtitle = if (settings.showWeather) "On" else "Off",
                        selected = settings.showWeather,
                        onClick = {
                            val next = !settings.showWeather
                            storage.setShowWeather(next)
                            settings = settings.copy(showWeather = next)
                        },
                    )
                }
            }
        }
    }

    if (showMarqueeDialog) {
        TextInputDialog(
            title = "Marquee text",
            defaultText = SettingsStorage.DEFAULT_MARQUEE_TEXT,
            maxLength = SettingsStorage.MAX_MARQUEE_TEXT_LENGTH,
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

    if (showThreeDTextDialog) {
        TextInputDialog(
            title = "3D Text",
            defaultText = SettingsStorage.DEFAULT_THREE_D_TEXT,
            maxLength = SettingsStorage.MAX_THREE_D_TEXT_LENGTH,
            value = threeDText,
            onValueChange = { value ->
                threeDText = if (value.text.length > SettingsStorage.MAX_THREE_D_TEXT_LENGTH) {
                    value.copy(text = value.text.take(SettingsStorage.MAX_THREE_D_TEXT_LENGTH))
                } else {
                    value
                }
            },
            onDismiss = {
                val finalText = threeDText.text.ifBlank { SettingsStorage.DEFAULT_THREE_D_TEXT }
                threeDText = TextFieldValue(finalText)
                storage.setThreeDText(finalText)
                showThreeDTextDialog = false
            },
        )
    }
}

@Composable
private fun LiveEffectPreview(settings: ScreensaverSettings, marqueeText: String, threeDText: String) {
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
        RuntimeSettings.speed = settings.speed
        key(settings.effect, marqueeText, threeDText) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { context ->
                    GLSurfaceView(context).apply {
                        setEGLContextClientVersion(3)
                        setRenderer(createPreviewRenderer(context, settings.effect, marqueeText, threeDText))
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                },
            )
        }
        PreviewSettingsOverlay(settings)
    }
}

@Composable
private fun BoxScope.PreviewSettingsOverlay(settings: ScreensaverSettings) {
    if (settings.brightness < 0.99f) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = (1f - settings.brightness) * 0.72f)),
        )
    }
    if (settings.showClock) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 16.dp),
        ) {
            Text(
                text = PREVIEW_TIME_FORMAT.format(Date()),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = PREVIEW_DATE_FORMAT.format(Date()),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (settings.showWeather) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "22\u00B0  Clear",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Sat  24\u00B0/17\u00B0", color = Color.White.copy(alpha = 0.86f), fontSize = 10.sp)
            Text("Sun  25\u00B0/18\u00B0", color = Color.White.copy(alpha = 0.86f), fontSize = 10.sp)
            Text("Mon  23\u00B0/16\u00B0", color = Color.White.copy(alpha = 0.86f), fontSize = 10.sp)
        }
    }
}

private fun createPreviewRenderer(
    context: android.content.Context,
    effect: ScreensaverEffect,
    marqueeText: String,
    threeDText: String,
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
        ScreensaverEffect.Photos -> PhotosRenderer(context)
        ScreensaverEffect.Ribbons -> RibbonsRenderer()
        ScreensaverEffect.Starfield -> StarfieldRenderer()
        ScreensaverEffect.ThreeDText -> ThreeDTextRenderer(
            context = context,
            previewText = threeDText.ifBlank { SettingsStorage.DEFAULT_THREE_D_TEXT },
        )
        ScreensaverEffect.WindowsEnergy -> WindowsEnergyRenderer()
        else -> PipesRenderer()
    }

private val PREVIEW_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.US)
private val PREVIEW_DATE_FORMAT = SimpleDateFormat("EEEE, MMMM d", Locale.US)

private fun nextSpeed(current: Float): Float {
    val values = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)
    val index = values.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return values[(if (index == -1) 2 else index + 1) % values.size]
}

private fun nextBrightness(current: Float): Float {
    val values = listOf(0.5f, 0.65f, 0.8f, 1f)
    val index = values.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return values[(if (index == -1) 3 else index + 1) % values.size]
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
        Text("Available now: 3D Pipes, Mystify, Beziers, 3D Maze, Starfield, Flying Windows, Marquee, Bubbles, 3D Text, Windows Energy, Ribbons, and Photos.", color = Color(0xFF9AA6BF))
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
private fun TextInputDialog(
    title: String,
    defaultText: String,
    maxLength: Int,
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
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
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
                        Text(defaultText, color = Color(0xFF687284))
                    }
                    innerTextField()
                },
            )
            Text(
                "${value.text.length}/$maxLength",
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
