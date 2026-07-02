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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MystifyRenderer : GLSurfaceView.Renderer {
    private val random = Random()
    private val figures = List(FIGURE_COUNT) { makeFigure(it) }
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var width = 1
    private var height = 1
    private var lastFrameNanos = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0.005f, 1f)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES30.glGetUniformLocation(program, "uColor")
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
            min(0.04f, (now - lastFrameNanos) / 1_000_000_000f)
        }) * RuntimeSettings.speed
        lastFrameNanos = now

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)
        GLES30.glLineWidth(3.4f)

        val aspect = width.toFloat() / height.toFloat()
        figures.forEach { figure ->
            updateFigure(figure, deltaSeconds, aspect)
            drawFigure(figure)
        }
    }

    private fun updateFigure(figure: Figure, deltaSeconds: Float, aspect: Float) {
        for (i in figure.points.indices) {
            val point = figure.points[i]
            val velocity = figure.velocities[i]
            point[0] += velocity[0] * deltaSeconds
            point[1] += velocity[1] * deltaSeconds

            if (point[0] > aspect || point[0] < -aspect) {
                point[0] = point[0].coerceIn(-aspect, aspect)
                velocity[0] = -velocity[0]
            }
            if (point[1] > 1f || point[1] < -1f) {
                point[1] = point[1].coerceIn(-1f, 1f)
                velocity[1] = -velocity[1]
            }
        }

        figure.hueOffset = (figure.hueOffset + deltaSeconds * 0.16f) % 1f
        figure.history.addFirst(figure.points.flatMap { listOf(it[0], it[1]) }.toFloatArray())
        while (figure.history.size > TRAIL_LENGTH) {
            figure.history.removeLast()
        }
    }

    private fun drawFigure(figure: Figure) {
        figure.history.forEachIndexed { index, points ->
            val age = index.toFloat() / TRAIL_LENGTH.toFloat()
            val alpha = (1f - age) * (1f - age) * 0.82f
            val color = colorFor((figure.colorPhase + figure.hueOffset + age * 0.18f) % 1f, alpha)
            GLES30.glUniform4f(colorHandle, color[0], color[1], color[2], color[3])

            val closed = FloatArray((POINT_COUNT + 1) * 2)
            System.arraycopy(points, 0, closed, 0, points.size)
            closed[POINT_COUNT * 2] = points[0]
            closed[POINT_COUNT * 2 + 1] = points[1]

            val buffer = makeBuffer(closed)
            GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 0, buffer)
            GLES30.glEnableVertexAttribArray(positionHandle)
            GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, POINT_COUNT + 1)
        }
    }

    private fun makeFigure(index: Int): Figure {
        val points = Array(POINT_COUNT) {
            floatArrayOf(randomSigned(0.8f), randomSigned(0.7f))
        }
        val velocities = Array(POINT_COUNT) {
            floatArrayOf(randomVelocity(), randomVelocity())
        }
        return Figure(
            points = points,
            velocities = velocities,
            colorPhase = index.toFloat() / FIGURE_COUNT.toFloat(),
        )
    }

    private fun randomVelocity(): Float {
        val value = 0.34f + random.nextFloat() * 0.62f
        return if (random.nextBoolean()) value else -value
    }

    private fun randomSigned(scale: Float): Float =
        (random.nextFloat() * 2f - 1f) * scale

    private fun colorFor(hue: Float, alpha: Float): FloatArray {
        val h = (hue * 6f).toInt()
        val f = hue * 6f - h
        val q = 1f - f
        val color = when (h % 6) {
            0 -> floatArrayOf(1f, f, 0f)
            1 -> floatArrayOf(q, 1f, 0f)
            2 -> floatArrayOf(0f, 1f, f)
            3 -> floatArrayOf(0f, q, 1f)
            4 -> floatArrayOf(f, 0f, 1f)
            else -> floatArrayOf(1f, 0f, q)
        }
        return floatArrayOf(color[0], color[1], color[2], alpha)
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

    private data class Figure(
        val points: Array<FloatArray>,
        val velocities: Array<FloatArray>,
        val colorPhase: Float,
        val history: ArrayDeque<FloatArray> = ArrayDeque(),
        var hueOffset: Float = 0f,
    )

    private companion object {
        private const val FIGURE_COUNT = 4
        private const val POINT_COUNT = 5
        private const val TRAIL_LENGTH = 34

        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;

            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
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
