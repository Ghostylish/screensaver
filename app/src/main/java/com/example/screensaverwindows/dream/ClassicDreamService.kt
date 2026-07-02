package com.example.screensaverwindows.dream

import android.opengl.GLSurfaceView
import android.service.dreams.DreamService
import android.widget.FrameLayout
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
import com.example.screensaverwindows.settings.SettingsStorage

class ClassicDreamService : DreamService() {
    private var glSurfaceView: GLSurfaceView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            val settings = SettingsStorage(this@ClassicDreamService).getSettings()
            val effect = settings.effect
            RuntimeSettings.speed = settings.speed
            setRenderer(
                when (effect) {
                    ScreensaverEffect.Beziers -> BeziersRenderer()
                    ScreensaverEffect.Bubbles -> BubblesRenderer()
                    ScreensaverEffect.FlyingWindows -> FlyingWindowsRenderer(this@ClassicDreamService)
                    ScreensaverEffect.Marquee -> MarqueeRenderer(this@ClassicDreamService)
                    ScreensaverEffect.Maze -> MazeRenderer()
                    ScreensaverEffect.Mystify -> MystifyRenderer()
                    ScreensaverEffect.Photos -> PhotosRenderer(this@ClassicDreamService)
                    ScreensaverEffect.Ribbons -> RibbonsRenderer()
                    ScreensaverEffect.Starfield -> StarfieldRenderer()
                    ScreensaverEffect.ThreeDText -> ThreeDTextRenderer(this@ClassicDreamService)
                    ScreensaverEffect.WindowsEnergy -> WindowsEnergyRenderer()
                    else -> PipesRenderer()
                },
            )
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        val content = FrameLayout(this).apply {
            addView(
                glSurfaceView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                DreamOverlayView(this@ClassicDreamService),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        setContentView(content)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        glSurfaceView?.onResume()
    }

    override fun onDreamingStopped() {
        glSurfaceView?.onPause()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        glSurfaceView = null
        super.onDetachedFromWindow()
    }
}
