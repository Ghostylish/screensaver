package com.example.screensaverwindows.ui.screens

import android.content.Intent
import android.location.Geocoder
import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.screensaverwindows.FullscreenScreensaverActivity
import com.example.screensaverwindows.R
import com.example.screensaverwindows.analytics.AnalyticsLogger
import com.example.screensaverwindows.renderer.BeziersRenderer
import com.example.screensaverwindows.renderer.BubblesRenderer
import com.example.screensaverwindows.renderer.FlyingWindowsRenderer
import com.example.screensaverwindows.renderer.MarqueeRenderer
import com.example.screensaverwindows.renderer.MazeRenderer
import com.example.screensaverwindows.renderer.MystifyRenderer
import com.example.screensaverwindows.renderer.PipesRenderer
import com.example.screensaverwindows.renderer.StarfieldRenderer
import com.example.screensaverwindows.renderer.ThreeDTextRenderer
import com.example.screensaverwindows.settings.RuntimeSettings
import com.example.screensaverwindows.settings.ScreensaverEffect
import com.example.screensaverwindows.settings.ScreensaverSettings
import com.example.screensaverwindows.settings.SettingsStorage
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
    var weatherCity by remember {
        mutableStateOf(TextFieldValue(storage.getWeatherCity()))
    }
    var weatherCityTitle by remember {
        mutableStateOf(storage.getWeatherCity())
    }
    var showMarqueeDialog by remember {
        mutableStateOf(false)
    }
    var showThreeDTextDialog by remember {
        mutableStateOf(false)
    }
    var showWeatherCityDialog by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        if (storage.hasResolvedWeatherLocation()) {
            val location = withContext(Dispatchers.IO) {
                localizedLocationFromCoordinates(
                context = context,
                latitude = storage.getWeatherLatitude(),
                longitude = storage.getWeatherLongitude(),
                fallbackCity = storage.getWeatherCity(),
                fallbackCountry = storage.getWeatherCountry(),
                fallbackCountryCode = storage.getWeatherCountryCode(),
                )
            }
            storage.setWeatherLocation(
                city = location.city,
                country = location.country,
                latitude = location.latitude,
                longitude = location.longitude,
                countryCode = location.countryCode,
            )
            weatherCity = TextFieldValue(location.city)
            weatherCityTitle = location.displayName
        } else if (storage.getWeatherCity() == SettingsStorage.DEFAULT_WEATHER_CITY) {
            runCatching { searchIpWeatherLocation(context) }.getOrNull()?.let { location ->
                storage.setWeatherLocation(
                    city = location.city,
                    country = location.country,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    countryCode = location.countryCode,
                )
                weatherCity = TextFieldValue(location.city)
                weatherCityTitle = location.displayName
            }
        }
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
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.app_description),
                    color = Color(0xFFB7C0D8),
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                )
                LiveEffectPreview(settings, marqueeText.text, threeDText.text, weatherCityTitle)
                SetupInstructions()
            }

            LazyColumn(
                modifier = Modifier.weight(1.05f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionTitle(stringResource(R.string.section_screensaver))
                }
                ScreensaverEffect.entries.groupBy { it.familyResId() }.forEach { (windowsFamilyResId, effects) ->
                    item {
                        FamilyHeader(stringResource(windowsFamilyResId))
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
                            effect == ScreensaverEffect.ThreeDText
                        OptionCard(
                            title = stringResource(effect.titleResId()),
                            subtitle = when {
                                effect == settings.effect -> stringResource(R.string.status_active)
                                available -> stringResource(R.string.status_ready)
                                else -> stringResource(R.string.status_coming_next)
                            },
                            selected = effect == settings.effect,
                            enabled = available,
                            onClick = {
                                storage.setEffect(effect)
                                settings = settings.copy(effect = effect)
                                AnalyticsLogger.screensaverSelected(context, effect)
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
                    SectionTitle(stringResource(R.string.section_settings))
                }
                item {
                    OptionCard(
                        title = stringResource(R.string.setting_speed),
                        subtitle = "${settings.speed}x",
                        selected = false,
                        onClick = {
                            val next = nextSpeed(settings.speed)
                            storage.setSpeed(next)
                            RuntimeSettings.speed = next
                            settings = settings.copy(speed = next)
                            AnalyticsLogger.speedChanged(context, next)
                        },
                    )
                }
                item {
                    OptionCard(
                        title = stringResource(R.string.setting_brightness),
                        subtitle = "${(settings.brightness * 100).toInt()}%",
                        selected = false,
                        onClick = {
                            val next = nextBrightness(settings.brightness)
                            storage.setBrightness(next)
                            settings = settings.copy(brightness = next)
                            AnalyticsLogger.brightnessChanged(context, next)
                        },
                    )
                }
                item {
                    OptionCard(
                        title = stringResource(R.string.setting_clock),
                        subtitle = if (settings.showClock) stringResource(R.string.toggle_on) else stringResource(R.string.toggle_off),
                        selected = settings.showClock,
                        onClick = {
                            val next = !settings.showClock
                            storage.setShowClock(next)
                            settings = settings.copy(showClock = next)
                            AnalyticsLogger.clockToggled(context, next)
                        },
                    )
                }
                item {
                    OptionCard(
                        title = stringResource(R.string.setting_weather),
                        subtitle = if (settings.showWeather) stringResource(R.string.toggle_on) else stringResource(R.string.toggle_off),
                        selected = settings.showWeather,
                        onClick = {
                            val next = !settings.showWeather
                            storage.setShowWeather(next)
                            settings = settings.copy(showWeather = next)
                            AnalyticsLogger.weatherToggled(context, next)
                        },
                    )
                }
                item {
                    OptionCard(
                        title = stringResource(R.string.setting_weather_city),
                        subtitle = weatherCityTitle,
                        selected = false,
                        onClick = {
                            weatherCity = TextFieldValue(storage.getWeatherCity())
                            showWeatherCityDialog = true
                        },
                    )
                }
                item {
                    OptionCard(
                        title = stringResource(R.string.setting_start_fullscreen),
                        subtitle = stringResource(R.string.setting_start_fullscreen_subtitle),
                        selected = false,
                        onClick = {
                            AnalyticsLogger.screensaverLaunchClicked(context, settings.effect)
                            context.startActivity(Intent(context, FullscreenScreensaverActivity::class.java))
                        },
                    )
                }
                item {
                    GoogleTvScreensaverNote()
                }
            }
        }
    }

    if (showMarqueeDialog) {
        TextInputDialog(
            title = stringResource(R.string.dialog_marquee_text),
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
                val usedDefault = marqueeText.text.isBlank()
                marqueeText = TextFieldValue(finalText)
                storage.setMarqueeText(finalText)
                AnalyticsLogger.marqueeTextSaved(context, finalText.length, usedDefault)
                showMarqueeDialog = false
            },
        )
    }

    if (showThreeDTextDialog) {
        TextInputDialog(
            title = stringResource(R.string.dialog_3d_text),
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
                val usedDefault = threeDText.text.isBlank()
                threeDText = TextFieldValue(finalText)
                storage.setThreeDText(finalText)
                AnalyticsLogger.threeDTextSaved(context, finalText.length, usedDefault)
                showThreeDTextDialog = false
            },
        )
    }

    if (showWeatherCityDialog) {
        WeatherCityDialog(
            value = weatherCity,
            onValueChange = { weatherCity = it },
            onApply = { location ->
                weatherCity = TextFieldValue(location.city)
                weatherCityTitle = location.displayName
                storage.setWeatherLocation(
                    city = location.city,
                    country = location.country,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    countryCode = location.countryCode,
                )
                AnalyticsLogger.weatherCityChanged(context)
                showWeatherCityDialog = false
            },
            onDismiss = {
                weatherCity = TextFieldValue(storage.getWeatherCity())
                showWeatherCityDialog = false
            },
        )
    }
}

@Composable
private fun LiveEffectPreview(
    settings: ScreensaverSettings,
    marqueeText: String,
    threeDText: String,
    weatherLocationTitle: String,
) {
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
        PreviewSettingsOverlay(settings, weatherLocationTitle)
    }
}

@Composable
private fun BoxScope.PreviewSettingsOverlay(settings: ScreensaverSettings, weatherLocationTitle: String) {
    val context = LocalContext.current
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
                text = previewDateFormat(context).format(Date()),
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
                text = weatherLocationTitle,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 10.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PreviewWeatherIcon()
                Text(
                    text = "${formatPreviewTemperature(22, context)}  ${stringResource(R.string.weather_clear)}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            previewForecastRows(context).forEach { row ->
                PreviewForecastRow(row.text, icon = row.icon)
            }
        }
    }
}

@Composable
private fun PreviewForecastRow(text: String, icon: PreviewWeatherIconType) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PreviewWeatherIcon(sizeDp = 11, type = icon)
        Text(text, color = Color.White.copy(alpha = 0.86f), fontSize = 10.sp)
    }
}

private enum class PreviewWeatherIconType {
    Clear,
    Partly,
    Rain,
}

@Composable
private fun PreviewWeatherIcon(sizeDp: Int = 22, type: PreviewWeatherIconType = PreviewWeatherIconType.Partly) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val r = size.minDimension * 0.20f
        val cx = size.width * 0.34f
        val cy = size.height * 0.36f
        if (type == PreviewWeatherIconType.Clear || type == PreviewWeatherIconType.Partly) {
            for (i in 0 until 8) {
                val angle = i * kotlin.math.PI.toFloat() / 4f
                drawLine(
                    color = Color(0xFFFFCA37),
                    start = androidx.compose.ui.geometry.Offset(
                        cx + kotlin.math.cos(angle) * r * 1.45f,
                        cy + kotlin.math.sin(angle) * r * 1.45f,
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        cx + kotlin.math.cos(angle) * r * 1.95f,
                        cy + kotlin.math.sin(angle) * r * 1.95f,
                    ),
                    strokeWidth = r * 0.25f,
                )
            }
            drawCircle(Color(0xFFFFCA37), radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy))
        }
        if (type != PreviewWeatherIconType.Clear) {
            val cloud = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(size.width * 0.26f, size.height * 0.42f, size.width * 0.58f, size.height * 0.74f))
                addOval(androidx.compose.ui.geometry.Rect(size.width * 0.43f, size.height * 0.32f, size.width * 0.78f, size.height * 0.72f))
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        androidx.compose.ui.geometry.Rect(size.width * 0.22f, size.height * 0.54f, size.width * 0.92f, size.height * 0.82f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f),
                    ),
                )
            }
            drawPath(cloud, Color.White.copy(alpha = 0.96f))
        }
        if (type == PreviewWeatherIconType.Rain) {
            drawLine(
                color = Color(0xFF69D2FF),
                start = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.78f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.96f),
                strokeWidth = size.minDimension * 0.06f,
            )
            drawLine(
                color = Color(0xFF69D2FF),
                start = androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.78f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.96f),
                strokeWidth = size.minDimension * 0.06f,
            )
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
        ScreensaverEffect.Starfield -> StarfieldRenderer()
        ScreensaverEffect.ThreeDText -> ThreeDTextRenderer(
            context = context,
            previewText = threeDText.ifBlank { SettingsStorage.DEFAULT_THREE_D_TEXT },
        )
        else -> PipesRenderer()
    }

private fun ScreensaverEffect.titleResId(): Int =
    when (this) {
        ScreensaverEffect.Pipes -> R.string.effect_pipes
        ScreensaverEffect.Mystify -> R.string.effect_mystify
        ScreensaverEffect.Beziers -> R.string.effect_beziers
        ScreensaverEffect.Maze -> R.string.effect_maze
        ScreensaverEffect.Starfield -> R.string.effect_starfield
        ScreensaverEffect.FlyingWindows -> R.string.effect_flying_windows
        ScreensaverEffect.Marquee -> R.string.effect_marquee
        ScreensaverEffect.Bubbles -> R.string.effect_bubbles
        ScreensaverEffect.ThreeDText -> R.string.effect_three_d_text
    }

private fun ScreensaverEffect.familyResId(): Int =
    when (this) {
        ScreensaverEffect.Pipes,
        ScreensaverEffect.Mystify,
        ScreensaverEffect.Beziers,
        ScreensaverEffect.Maze,
        ScreensaverEffect.Starfield,
        ScreensaverEffect.FlyingWindows,
        ScreensaverEffect.Marquee -> R.string.family_windows_xp
        ScreensaverEffect.Bubbles,
        ScreensaverEffect.ThreeDText -> R.string.family_windows_vista
    }

@Composable
private fun WeatherCityDialog(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onApply: (WeatherLocationSuggestion) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var lookupState by remember {
        mutableStateOf<WeatherCityLookupState>(WeatherCityLookupState.Idle)
    }
    var fallbackLocation by remember {
        mutableStateOf(londonFallbackLocation())
    }
    val normalizedQuery = value.text.trim()

    LaunchedEffect(Unit) {
        fallbackLocation = runCatching { searchIpWeatherLocation(context) }.getOrElse { londonFallbackLocation() }
    }

    LaunchedEffect(normalizedQuery) {
        lookupState = when {
            normalizedQuery.isBlank() -> WeatherCityLookupState.Default
            normalizedQuery.length < 2 -> WeatherCityLookupState.Idle
            else -> {
                delay(350L)
                WeatherCityLookupState.Searching
            }
        }
        if (normalizedQuery.length >= 2) {
            lookupState = runCatching {
                searchWeatherCity(context, normalizedQuery)
            }.getOrNull()?.let { result ->
                WeatherCityLookupState.Found(result)
            } ?: WeatherCityLookupState.NotFound
        }
    }

    val locationToApply = when (val state = lookupState) {
        is WeatherCityLookupState.Found -> state.location
        else -> fallbackLocation
    }
    val statusText = when (val state = lookupState) {
        WeatherCityLookupState.Default -> stringResource(R.string.weather_lookup_default_ip, fallbackLocation.displayName)
        is WeatherCityLookupState.Found -> stringResource(R.string.weather_lookup_found, state.location.displayName)
        WeatherCityLookupState.Idle -> stringResource(R.string.weather_lookup_idle)
        WeatherCityLookupState.NotFound -> stringResource(R.string.weather_lookup_not_found, fallbackLocation.displayName)
        WeatherCityLookupState.Searching -> stringResource(R.string.weather_lookup_searching)
    }
    val statusColor = when (lookupState) {
        is WeatherCityLookupState.Found -> Color(0xFF62F2FF)
        WeatherCityLookupState.NotFound -> Color(0xFFFFC66D)
        else -> Color(0xFF9AA6BF)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF111827))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.setting_weather_city), color = Color.White, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = value,
                onValueChange = { nextValue ->
                    onValueChange(
                        if (nextValue.text.length > SettingsStorage.MAX_WEATHER_CITY_LENGTH) {
                            nextValue.copy(text = nextValue.text.take(SettingsStorage.MAX_WEATHER_CITY_LENGTH))
                        } else {
                            nextValue
                        },
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF07090F))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { innerTextField ->
                    if (value.text.isEmpty()) {
                        Text(SettingsStorage.DEFAULT_WEATHER_CITY, color = Color(0xFF687284))
                    }
                    innerTextField()
                },
            )
            Text(statusText, color = statusColor, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${value.text.length}/${SettingsStorage.MAX_WEATHER_CITY_LENGTH}",
                color = Color(0xFF9AA6BF),
                style = MaterialTheme.typography.bodySmall,
            )
            OptionCard(
                title = stringResource(R.string.action_apply),
                subtitle = locationToApply.displayName,
                selected = true,
                onClick = { onApply(locationToApply) },
            )
        }
    }
}

@Composable
private fun SetupInstructions() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.setup_instruction_system),
            color = Color(0xFFB7C0D8),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GoogleTvScreensaverNote() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.google_tv_blocked_note),
            color = Color(0xFFB7C0D8),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.google_tv_fullscreen_note),
            color = Color(0xFF8D98AD),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private val PREVIEW_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.US)
private const val DEFAULT_WEATHER_LATITUDE = 51.5072f
private const val DEFAULT_WEATHER_LONGITUDE = -0.1276f

private fun previewDateFormat(context: android.content.Context): SimpleDateFormat =
    SimpleDateFormat("EEEE, MMMM d", supportedAppLocale(context))

private fun previewForecastRows(context: android.content.Context): List<PreviewForecastSample> {
    val dayFormat = SimpleDateFormat("EEE", supportedAppLocale(context))
    val calendar = java.util.Calendar.getInstance()
    return listOf(
        PreviewWeatherIconType.Partly to (24 to 17),
        PreviewWeatherIconType.Clear to (25 to 18),
        PreviewWeatherIconType.Rain to (23 to 16),
    ).map { (icon, temps) ->
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        PreviewForecastSample(
            "${dayFormat.format(calendar.time)}  ${formatPreviewTemperature(temps.first, context)}/${formatPreviewTemperature(temps.second, context)}",
            icon,
        )
    }
}

private fun formatPreviewTemperature(celsius: Int, context: android.content.Context): String {
    val storage = SettingsStorage(context)
    val unit = temperatureUnitFor(storage.getWeatherCountryCode(), storage.getWeatherCountry())
    val value = if (unit == TemperatureUnit.Fahrenheit) (celsius * 9f / 5f + 32f).toInt() else celsius
    return "$value${unit.displaySuffix}"
}

private fun supportedAppLocale(context: android.content.Context): Locale {
    val systemLocale = context.resources.configuration.locales[0]
    return when (systemLocale.language) {
        "ru" -> Locale.forLanguageTag("ru-RU")
        "uk" -> Locale.forLanguageTag("uk-UA")
        else -> Locale.US
    }
}

private fun weatherApiLanguage(context: android.content.Context): String =
    when (supportedAppLocale(context).language) {
        "ru" -> "ru"
        "uk" -> "uk"
        else -> "en"
    }

private suspend fun searchWeatherCity(context: android.content.Context, query: String): WeatherLocationSuggestion? =
    withContext(Dispatchers.IO) {
        searchWeatherCityByQuery(context, query)
            ?: transliterateCyrillicToLatin(query)
                .takeIf { it.isNotBlank() && !it.equals(query, ignoreCase = true) }
                ?.let { searchWeatherCityByQuery(context, it) }
    }

private fun searchWeatherCityByQuery(
    context: android.content.Context,
    query: String,
): WeatherLocationSuggestion? {
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val url = "https://geocoding-api.open-meteo.com/v1/search" +
        "?name=$encodedQuery&count=1&language=${weatherApiLanguage(context)}&format=json"
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 7_000
        readTimeout = 7_000
    }
    val text = connection.inputStream.bufferedReader().use(BufferedReader::readText)
    val result = JSONObject(text).optJSONArray("results")?.takeIf { it.length() > 0 }
        ?.getJSONObject(0)
        ?: return null
    return WeatherLocationSuggestion(
        city = result.optString("name", SettingsStorage.DEFAULT_WEATHER_CITY),
        country = result.optString("country", SettingsStorage.DEFAULT_WEATHER_COUNTRY),
        countryCode = result.optString("country_code", SettingsStorage.DEFAULT_WEATHER_COUNTRY_CODE).uppercase(),
        latitude = result.getDouble("latitude").toFloat(),
        longitude = result.getDouble("longitude").toFloat(),
    )
}

private fun transliterateCyrillicToLatin(text: String): String =
    buildString {
        text.forEach { char ->
            append(
                when (char.lowercaseChar()) {
                    'а' -> "a"
                    'б' -> "b"
                    'в' -> "v"
                    'г' -> "h"
                    'ґ' -> "g"
                    'д' -> "d"
                    'е' -> "e"
                    'ё' -> "yo"
                    'є' -> "ye"
                    'ж' -> "zh"
                    'з' -> "z"
                    'и' -> "y"
                    'і' -> "i"
                    'ї' -> "yi"
                    'й' -> "y"
                    'к' -> "k"
                    'л' -> "l"
                    'м' -> "m"
                    'н' -> "n"
                    'о' -> "o"
                    'п' -> "p"
                    'р' -> "r"
                    'с' -> "s"
                    'т' -> "t"
                    'у' -> "u"
                    'ф' -> "f"
                    'х' -> "kh"
                    'ц' -> "ts"
                    'ч' -> "ch"
                    'ш' -> "sh"
                    'щ' -> "shch"
                    'ы' -> "y"
                    'э' -> "e"
                    'ю' -> "yu"
                    'я' -> "ya"
                    'ь', 'ъ', '\'' -> ""
                    else -> char.toString()
                },
            )
        }
    }

private suspend fun searchIpWeatherLocation(context: android.content.Context): WeatherLocationSuggestion =
    withContext(Dispatchers.IO) {
        val connection = (URL("https://ipapi.co/json/").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 7_000
            setRequestProperty("User-Agent", "RetroScreensaverCollection-AndroidTV")
        }
        val text = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        val json = JSONObject(text)
        val latitude = json.optDouble("latitude", DEFAULT_WEATHER_LATITUDE.toDouble()).toFloat()
        val longitude = json.optDouble("longitude", DEFAULT_WEATHER_LONGITUDE.toDouble()).toFloat()
        localizedLocationFromCoordinates(
            context = context,
            latitude = latitude,
            longitude = longitude,
            fallbackCity = json.optString("city").takeIf { it.isNotBlank() } ?: SettingsStorage.DEFAULT_WEATHER_CITY,
            fallbackCountry = json.optString("country_name").takeIf { it.isNotBlank() } ?: SettingsStorage.DEFAULT_WEATHER_COUNTRY,
            fallbackCountryCode = json.optString("country_code").takeIf { it.isNotBlank() } ?: SettingsStorage.DEFAULT_WEATHER_COUNTRY_CODE,
        )
    }

private fun localizedLocationFromCoordinates(
    context: android.content.Context,
    latitude: Float,
    longitude: Float,
    fallbackCity: String,
    fallbackCountry: String,
    fallbackCountryCode: String,
): WeatherLocationSuggestion {
    runCatching {
        searchWeatherCityNear(context, fallbackCity, fallbackCountry, fallbackCountryCode, latitude, longitude)
    }.getOrNull()?.let { return it }

    val address = runCatching {
        Geocoder(context, supportedAppLocale(context)).getFromLocation(latitude.toDouble(), longitude.toDouble(), 1)
            ?.firstOrNull()
    }.getOrNull()
    return WeatherLocationSuggestion(
        city = address?.locality?.takeIf { it.isNotBlank() }
            ?: address?.subAdminArea?.takeIf { it.isNotBlank() }
            ?: fallbackCity,
        country = address?.countryName?.takeIf { it.isNotBlank() } ?: fallbackCountry,
        countryCode = address?.countryCode?.takeIf { it.isNotBlank() }?.uppercase() ?: fallbackCountryCode,
        latitude = latitude,
        longitude = longitude,
    )
}

private fun searchWeatherCityNear(
    context: android.content.Context,
    city: String,
    fallbackCountry: String,
    fallbackCountryCode: String,
    latitude: Float,
    longitude: Float,
): WeatherLocationSuggestion? {
    val encodedCity = URLEncoder.encode(city, "UTF-8")
    val url = "https://geocoding-api.open-meteo.com/v1/search" +
        "?name=$encodedCity&count=10&language=${weatherApiLanguage(context)}&format=json"
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 7_000
        readTimeout = 7_000
    }
    val text = connection.inputStream.bufferedReader().use(BufferedReader::readText)
    val results = JSONObject(text).optJSONArray("results") ?: return null
    if (results.length() == 0) return null
    val nearest = (0 until results.length())
        .map { results.getJSONObject(it) }
        .minByOrNull { result ->
            val latDelta = result.getDouble("latitude").toFloat() - latitude
            val lonDelta = result.getDouble("longitude").toFloat() - longitude
            latDelta * latDelta + lonDelta * lonDelta
        }
        ?: return null
    return WeatherLocationSuggestion(
        city = nearest.optString("name", city),
        country = nearest.optString("country", fallbackCountry),
        countryCode = nearest.optString("country_code", fallbackCountryCode).uppercase(),
        latitude = latitude,
        longitude = longitude,
    )
}

private fun londonFallbackLocation(): WeatherLocationSuggestion =
    WeatherLocationSuggestion(
        city = SettingsStorage.DEFAULT_WEATHER_CITY,
        country = SettingsStorage.DEFAULT_WEATHER_COUNTRY,
        countryCode = SettingsStorage.DEFAULT_WEATHER_COUNTRY_CODE,
        latitude = DEFAULT_WEATHER_LATITUDE,
        longitude = DEFAULT_WEATHER_LONGITUDE,
    )

private sealed interface WeatherCityLookupState {
    data object Idle : WeatherCityLookupState
    data object Default : WeatherCityLookupState
    data object Searching : WeatherCityLookupState
    data object NotFound : WeatherCityLookupState
    data class Found(val location: WeatherLocationSuggestion) : WeatherCityLookupState
}

private data class WeatherLocationSuggestion(
    val city: String,
    val country: String,
    val countryCode: String,
    val latitude: Float,
    val longitude: Float,
) {
    val displayName: String = if (country.isBlank()) city else "$city, $country"
}

private enum class TemperatureUnit(val displaySuffix: String) {
    Celsius("\u00B0C"),
    Fahrenheit("\u00B0F"),
}

private fun temperatureUnitFor(countryCode: String, country: String): TemperatureUnit {
    val normalizedCode = countryCode.trim().uppercase(Locale.US)
    val normalizedCountry = country.trim().lowercase(Locale.US)
    return if (normalizedCode in FAHRENHEIT_COUNTRY_CODES || normalizedCountry in FAHRENHEIT_COUNTRY_NAMES) {
        TemperatureUnit.Fahrenheit
    } else {
        TemperatureUnit.Celsius
    }
}

private val FAHRENHEIT_COUNTRY_CODES = setOf("US", "AS", "BS", "BZ", "GU", "KY", "MP", "PR", "PW", "UM", "VI")
private val FAHRENHEIT_COUNTRY_NAMES = setOf(
    "united states",
    "united states of america",
    "bahamas",
    "belize",
    "cayman islands",
    "palau",
)

private data class PreviewForecastSample(
    val text: String,
    val icon: PreviewWeatherIconType,
)

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
                title = stringResource(R.string.action_apply),
                subtitle = if (value.text.isBlank()) stringResource(R.string.default_text_will_be_used) else value.text,
                selected = true,
                onClick = onDismiss,
            )
        }
    }
}
