package com.example.screensaverwindows.dream

import android.opengl.GLSurfaceView
import android.service.dreams.DreamService
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
            val effect = SettingsStorage(this@ClassicDreamService).getEffect()
            setRenderer(
                when (effect) {
                    ScreensaverEffect.Beziers -> BeziersRenderer()
                    ScreensaverEffect.Bubbles -> BubblesRenderer()
                    ScreensaverEffect.FlyingWindows -> FlyingWindowsRenderer(this@ClassicDreamService)
                    ScreensaverEffect.Marquee -> MarqueeRenderer(this@ClassicDreamService)
                    ScreensaverEffect.Maze -> MazeRenderer()
                    ScreensaverEffect.Mystify -> MystifyRenderer()
                    ScreensaverEffect.Ribbons -> RibbonsRenderer()
                    ScreensaverEffect.Starfield -> StarfieldRenderer()
                    else -> PipesRenderer()
                },
            )
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        setContentView(glSurfaceView)
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
