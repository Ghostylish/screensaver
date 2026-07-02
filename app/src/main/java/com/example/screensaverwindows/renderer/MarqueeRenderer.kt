package com.example.screensaverwindows.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import com.example.screensaverwindows.settings.SettingsStorage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

class MarqueeRenderer(
    private val context: Context,
    private val previewText: String? = null,
) : GLSurfaceView.Renderer {
    private lateinit var quadBuffer: FloatBuffer
    private var program = 0
    private var texture = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var offsetHandle = 0
    private var scaleHandle = 0
    private var colorHandle = 0
    private var textureHandle = 0
    private var lastFrameNanos = 0L
    private var scrollX = 1.25f
    private var textWidthNdc = 1.6f
    private var textureAspect = 1f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES30.glGetAttribLocation(program, "aTexCoord")
        offsetHandle = GLES30.glGetUniformLocation(program, "uOffset")
        scaleHandle = GLES30.glGetUniformLocation(program, "uScale")
        colorHandle = GLES30.glGetUniformLocation(program, "uColor")
        textureHandle = GLES30.glGetUniformLocation(program, "uTexture")
        quadBuffer = makeBuffer(QUAD_VERTICES)
        texture = createTextTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        val safeWidth = max(1, width)
        val safeHeight = max(1, height)
        GLES30.glViewport(0, 0, safeWidth, safeHeight)
        val screenAspect = safeWidth.toFloat() / safeHeight.toFloat()
        textWidthNdc = textureAspect * TEXT_HEIGHT_NDC / screenAspect
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val deltaSeconds = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((now - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.04f)
        }
        lastFrameNanos = now

        scrollX -= SCROLL_SPEED * deltaSeconds
        if (scrollX < -1.18f - textWidthNdc) {
            scrollX = 1.18f
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glUniform1i(textureHandle, 0)
        GLES30.glUniform2f(offsetHandle, scrollX, 0f)
        GLES30.glUniform2f(scaleHandle, textWidthNdc, TEXT_HEIGHT_NDC)
        GLES30.glUniform4f(colorHandle, 1f, 0.86f, 0.06f, 1f)

        quadBuffer.position(0)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, STRIDE_BYTES, quadBuffer)
        GLES30.glEnableVertexAttribArray(positionHandle)
        quadBuffer.position(2)
        GLES30.glVertexAttribPointer(texCoordHandle, 2, GLES30.GL_FLOAT, false, STRIDE_BYTES, quadBuffer)
        GLES30.glEnableVertexAttribArray(texCoordHandle)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, QUAD_VERTEX_COUNT)
    }

    private fun createTextTexture(): Int {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 92f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(8f, 5f, 5f, Color.argb(180, 0, 0, 0))
        }
        val text = previewText?.ifBlank { SettingsStorage.DEFAULT_MARQUEE_TEXT }
            ?: SettingsStorage(context).getMarqueeText().ifBlank { SettingsStorage.DEFAULT_MARQUEE_TEXT }
        val measuredWidth = paint.measureText(text).toInt() + TEXT_PADDING * 2
        val textureWidth = measuredWidth.coerceIn(MIN_TEXTURE_WIDTH, MAX_TEXTURE_WIDTH)
        textureAspect = textureWidth.toFloat() / TEXTURE_HEIGHT.toFloat()

        val bitmap = Bitmap.createBitmap(textureWidth, TEXTURE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val x = 26f
        val y = TEXTURE_HEIGHT * 0.63f
        canvas.drawText(text, x, y, paint)

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return textures[0]
    }

    private fun buildProgram(vertexShader: String, fragmentShader: String): Int {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, vertexShader)
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShader)
        val linkedProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(linkedProgram, vertex)
        GLES30.glAttachShader(linkedProgram, fragment)
        GLES30.glLinkProgram(linkedProgram)
        val status = IntArray(1)
        GLES30.glGetProgramiv(linkedProgram, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(linkedProgram)
            GLES30.glDeleteProgram(linkedProgram)
            throw IllegalStateException("OpenGL program link failed: $log")
        }
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        return linkedProgram
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("OpenGL shader compile failed: $log")
        }
        return shader
    }

    private fun makeBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer
            .allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private companion object {
        private const val MIN_TEXTURE_WIDTH = 1024
        private const val MAX_TEXTURE_WIDTH = 4096
        private const val TEXTURE_HEIGHT = 192
        private const val TEXT_PADDING = 52
        private const val TEXT_HEIGHT_NDC = 0.28f
        private const val SCROLL_SPEED = 0.52f
        private const val FLOATS_PER_VERTEX = 4
        private const val STRIDE_BYTES = FLOATS_PER_VERTEX * Float.SIZE_BYTES
        private const val QUAD_VERTEX_COUNT = 6

        private val QUAD_VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 1f,
            0.5f, -0.5f, 1f, 1f,
            0.5f, 0.5f, 1f, 0f,
            -0.5f, -0.5f, 0f, 1f,
            0.5f, 0.5f, 1f, 0f,
            -0.5f, 0.5f, 0f, 0f,
        )

        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            uniform vec2 uOffset;
            uniform vec2 uScale;
            varying vec2 vTexCoord;

            void main() {
                vTexCoord = aTexCoord;
                vec2 position = aPosition * uScale + uOffset;
                gl_Position = vec4(position, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec4 uColor;
            varying vec2 vTexCoord;

            void main() {
                vec4 sampleColor = texture2D(uTexture, vTexCoord);
                vec3 color = floor(uColor.rgb * 8.0) / 8.0;
                gl_FragColor = vec4(color, sampleColor.a * uColor.a);
            }
        """
    }
}
