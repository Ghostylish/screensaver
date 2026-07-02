package com.example.screensaverwindows.renderer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.example.screensaverwindows.settings.RuntimeSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min

class StarfieldRenderer : GLSurfaceView.Renderer {
    private val random = Random()
    private val stars = Array(STAR_COUNT) { makeStar() }
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var pointSizeHandle = 0
    private var width = 1
    private var height = 1
    private var lastFrameNanos = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0.012f, 1f)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES30.glGetUniformLocation(program, "uColor")
        pointSizeHandle = GLES30.glGetUniformLocation(program, "uPointSize")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = max(1, width)
        this.height = max(1, height)
        GLES30.glViewport(0, 0, this.width, this.height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val deltaSeconds = (if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            min(0.05f, (now - lastFrameNanos) / 1_000_000_000f)
        }) * RuntimeSettings.speed
        lastFrameNanos = now

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)

        val aspect = width.toFloat() / height.toFloat()
        GLES30.glLineWidth(1.7f)
        stars.forEach { star ->
            updateStar(star, deltaSeconds)
            drawStar(star, aspect)
        }
    }

    private fun updateStar(star: Star, deltaSeconds: Float) {
        star.previousZ = star.z
        star.z -= (STAR_SPEED + star.speedBoost) * deltaSeconds
        star.rotation += star.rotationSpeed * deltaSeconds
        if (star.z <= NEAR_Z) {
            resetStar(star, FAR_Z)
        }
    }

    private fun drawStar(star: Star, aspect: Float) {
        val x = rotatedX(star)
        val y = rotatedY(star)
        val current = project(x, y, star.z, aspect)
        val trailZ = star.z + (star.previousZ - star.z) * TRAIL_SCALE
        val previous = project(x, y, trailZ, aspect)

        if (!isVisible(current[0], current[1]) || !isVisible(previous[0], previous[1])) {
            resetStar(star, FAR_Z)
            return
        }

        val depth = 1f - ((star.z - NEAR_Z) / (FAR_Z - NEAR_Z)).coerceIn(0f, 1f)
        val alpha = (0.18f + depth * 0.58f) * star.twinkle
        val color = star.color
        GLES30.glUniform4f(colorHandle, color[0], color[1], color[2], alpha)

        val line = floatArrayOf(previous[0], previous[1], current[0], current[1])
        val lineBuffer = makeBuffer(line)
        GLES30.glUniform1f(pointSizeHandle, 1f)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 0, lineBuffer)
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, 2)

        val pointSize = 1.5f + depth * 5.2f
        val point = floatArrayOf(current[0], current[1])
        val pointBuffer = makeBuffer(point)
        GLES30.glUniform1f(pointSizeHandle, pointSize)
        GLES30.glUniform4f(colorHandle, color[0], color[1], color[2], min(1f, alpha + 0.16f))
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 0, pointBuffer)
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 1)
    }

    private fun project(x: Float, y: Float, z: Float, aspect: Float): FloatArray {
        val perspective = FOCAL_LENGTH / z
        return floatArrayOf(
            x * perspective / aspect,
            y * perspective,
        )
    }

    private fun isVisible(x: Float, y: Float): Boolean =
        x > -1.15f && x < 1.15f && y > -1.15f && y < 1.15f

    private fun rotatedX(star: Star): Float =
        star.x + star.y * star.rotation * 0.018f

    private fun rotatedY(star: Star): Float =
        star.y - star.x * star.rotation * 0.018f

    private fun makeStar(): Star =
        Star().also { resetStar(it, randomDepth()) }

    private fun resetStar(star: Star, z: Float) {
        star.x = randomSigned(SPAWN_RADIUS)
        star.y = randomSigned(SPAWN_RADIUS * 0.78f)
        star.z = z
        star.previousZ = z + 0.05f
        star.speedBoost = random.nextFloat() * 0.18f
        star.rotation = randomSigned(1f)
        star.rotationSpeed = randomSigned(0.32f)
        star.twinkle = 0.62f + random.nextFloat() * 0.46f
        star.color = STAR_COLORS[random.nextInt(STAR_COLORS.size)]
    }

    private fun randomDepth(): Float =
        NEAR_Z + random.nextFloat() * (FAR_Z - NEAR_Z)

    private fun randomSigned(scale: Float): Float =
        (random.nextFloat() * 2f - 1f) * scale

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

    private class Star {
        var x = 0f
        var y = 0f
        var z = 0f
        var previousZ = 0f
        var speedBoost = 0f
        var rotation = 0f
        var rotationSpeed = 0f
        var twinkle = 1f
        var color = STAR_COLORS[0]
    }

    private companion object {
        private const val STAR_COUNT = 680
        private const val SPAWN_RADIUS = 2.35f
        private const val NEAR_Z = 0.20f
        private const val FAR_Z = 4.8f
        private const val FOCAL_LENGTH = 0.44f
        private const val STAR_SPEED = 0.32f
        private const val TRAIL_SCALE = 0.26f

        private val STAR_COLORS = arrayOf(
            floatArrayOf(1f, 1f, 1f),
            floatArrayOf(0.68f, 0.86f, 1f),
            floatArrayOf(0.92f, 0.96f, 1f),
            floatArrayOf(1f, 0.86f, 0.62f),
            floatArrayOf(0.66f, 1f, 0.94f),
        )

        private const val VERTEX_SHADER = """
            uniform float uPointSize;
            attribute vec2 aPosition;

            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                gl_PointSize = uPointSize;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;

            void main() {
                gl_FragColor = uColor;
            }
        """
    }
}
