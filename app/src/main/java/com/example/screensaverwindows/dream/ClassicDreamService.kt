package com.example.screensaverwindows.dream

import android.opengl.GLSurfaceView
import android.service.dreams.DreamService
import com.example.screensaverwindows.analytics.AnalyticsLogger
import com.example.screensaverwindows.settings.SettingsStorage

class ClassicDreamService : DreamService() {
    private var glSurfaceView: GLSurfaceView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true

        val (content, surfaceView) = createScreensaverContent(this)
        AnalyticsLogger.screensaverStarted(this, SettingsStorage(this).getEffect(), "dream_service")
        glSurfaceView = surfaceView
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
