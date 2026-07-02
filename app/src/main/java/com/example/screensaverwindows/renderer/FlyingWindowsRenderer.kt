package com.example.screensaverwindows.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import androidx.core.content.ContextCompat
import com.example.screensaverwindows.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.max

class FlyingWindowsRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private val random = Random()
    private val logos = Array(LOGO_COUNT) { makeLogo() }
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val modelView = FloatArray(16)
    private val mvp = FloatArray(16)

    private lateinit var paneBuffer: FloatBuffer
    private var logoTexture = 0
    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var colorHandle = 0
    private var mvpHandle = 0
    private var textureHandle = 0
    private var width = 1
    private var height = 1
    private var lastFrameNanos = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.018f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES30.glGetAttribLocation(program, "aTexCoord")
        colorHandle = GLES30.glGetUniformLocation(program, "uColor")
        mvpHandle = GLES30.glGetUniformLocation(program, "uMvp")
        textureHandle = GLES30.glGetUniformLocation(program, "uTexture")
        paneBuffer = makeBuffer(LOGO_VERTICES)
        logoTexture = createLogoTexture()
        Matrix.setLookAtM(view, 0, 0f, 0f, 0.35f, 0f, 0f, -8f, 0f, 1f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = max(1, width)
        this.height = max(1, height)
        GLES30.glViewport(0, 0, this.width, this.height)
        val aspect = this.width.toFloat() / this.height.toFloat()
        Matrix.perspectiveM(projection, 0, 48f, aspect, 0.1f, 80f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val deltaSeconds = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((now - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.04f)
        }
        lastFrameNanos = now

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, logoTexture)
        GLES30.glUniform1i(textureHandle, 0)

        logos.forEach { logo ->
            updateLogo(logo, deltaSeconds)
            drawLogo(logo)
        }
    }

    private fun updateLogo(logo: FlyingLogo, deltaSeconds: Float) {
        logo.z += logo.speed * deltaSeconds
        val depthProgress = ((logo.z - FAR_Z) / (NEAR_Z - FAR_Z)).coerceIn(0f, 1f)
        val sidePush = depthProgress * depthProgress * 2.25f
        logo.x += (logo.driftX + logo.originX * sidePush) * deltaSeconds
        logo.y += (logo.driftY + logo.originY * sidePush) * deltaSeconds

        if (logo.z > NEAR_Z || abs(logo.x) > RESET_X || abs(logo.y) > RESET_Y) {
            resetLogo(logo, FAR_Z)
        }
    }

    private fun drawLogo(logo: FlyingLogo) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, logo.x, logo.y, logo.z)
        Matrix.scaleM(model, 0, LOGO_SCALE * LOGO_ASPECT, LOGO_SCALE, LOGO_SCALE)

        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)

        GLES30.glUniform4f(colorHandle, logo.color[0], logo.color[1], logo.color[2], 0.94f)
        paneBuffer.position(0)
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, STRIDE_BYTES, paneBuffer)
        GLES30.glEnableVertexAttribArray(positionHandle)
        paneBuffer.position(3)
        GLES30.glVertexAttribPointer(texCoordHandle, 2, GLES30.GL_FLOAT, false, STRIDE_BYTES, paneBuffer)
        GLES30.glEnableVertexAttribArray(texCoordHandle)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, LOGO_VERTEX_COUNT)
    }

    private fun makeLogo(): FlyingLogo {
        return FlyingLogo(color = RGB_COLORS[random.nextInt(RGB_COLORS.size)]).also {
            resetLogo(it, randomDepth())
        }
    }

    private fun resetLogo(logo: FlyingLogo, z: Float) {
        logo.x = randomSigned(SPAWN_X)
        logo.y = randomSigned(SPAWN_Y)
        logo.originX = logo.x
        logo.originY = logo.y
        logo.z = z
        logo.driftX = randomVelocity(0.015f, 0.07f)
        logo.driftY = randomVelocity(0.01f, 0.05f)
        logo.speed = 4.20f + random.nextFloat() * 2.10f
        logo.color = RGB_COLORS[random.nextInt(RGB_COLORS.size)]
    }

    private fun randomVelocity(min: Float, max: Float): Float {
        val value = min + random.nextFloat() * (max - min)
        return if (random.nextBoolean()) value else -value
    }

    private fun randomSigned(scale: Float): Float =
        (random.nextFloat() * 2f - 1f) * scale

    private fun randomDepth(): Float =
        FAR_Z + random.nextFloat() * (START_Z_SPREAD)

    private fun createLogoTexture(): Int {
        val drawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.windowsnt_logo)).mutate()
        val bitmap = Bitmap.createBitmap(TEXTURE_WIDTH, TEXTURE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT)
        drawable.draw(canvas)

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

    private data class FlyingLogo(
        var x: Float = 0f,
        var y: Float = 0f,
        var originX: Float = 0f,
        var originY: Float = 0f,
        var z: Float = 0f,
        var driftX: Float = 0f,
        var driftY: Float = 0f,
        var speed: Float = 0f,
        var color: FloatArray,
    )

    private companion object {
        private const val LOGO_COUNT = 44
        private const val SPAWN_X = 11.5f
        private const val SPAWN_Y = 6.4f
        private const val RESET_X = 8.8f
        private const val RESET_Y = 5.0f
        private const val FAR_Z = -18.0f
        private const val START_Z_SPREAD = 15.0f
        private const val NEAR_Z = -0.35f
        private const val LOGO_SCALE = 0.86f
        private const val LOGO_ASPECT = 1918f / 1536f
        private const val LOGO_VERTEX_COUNT = 6
        private const val FLOATS_PER_VERTEX = 5
        private const val STRIDE_BYTES = FLOATS_PER_VERTEX * Float.SIZE_BYTES
        private const val TEXTURE_WIDTH = 256
        private const val TEXTURE_HEIGHT = 205

        private val RGB_COLORS = arrayOf(
            floatArrayOf(1.00f, 0.04f, 0.03f),
            floatArrayOf(0.03f, 0.95f, 0.08f),
            floatArrayOf(0.08f, 0.30f, 1.00f),
            floatArrayOf(1.00f, 0.86f, 0.02f),
            floatArrayOf(0.00f, 0.95f, 0.95f),
            floatArrayOf(1.00f, 0.12f, 0.85f),
            floatArrayOf(1.00f, 0.45f, 0.02f),
            floatArrayOf(0.72f, 0.22f, 1.00f),
            floatArrayOf(0.92f, 0.92f, 0.92f),
        )

        private val LOGO_VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 1f,
            0.5f, -0.5f, 0f, 1f, 1f,
            0.5f, 0.5f, 0f, 1f, 0f,
            -0.5f, -0.5f, 0f, 0f, 1f,
            0.5f, 0.5f, 0f, 1f, 0f,
            -0.5f, 0.5f, 0f, 0f, 0f,
        )

        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;

            void main() {
                vTexCoord = aTexCoord;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;

            void main() {
                vec4 sampleColor = texture2D(uTexture, vTexCoord);
                vec3 color = floor(uColor.rgb * 8.0) / 8.0;
                gl_FragColor = vec4(color, sampleColor.a * uColor.a);
            }
        """
    }
}
