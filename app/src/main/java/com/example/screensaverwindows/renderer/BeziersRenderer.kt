package com.example.screensaverwindows.renderer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min

class BeziersRenderer : GLSurfaceView.Renderer {
    private val random = Random()
    private val figures = List(FIGURE_COUNT) { makeFigure(it) }
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var width = 1
    private var height = 1
    private var lastFrameNanos = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
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
        val deltaSeconds = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            min(0.04f, (now - lastFrameNanos) / 1_000_000_000f)
        }
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

        figure.hueOffset = (figure.hueOffset + deltaSeconds * 0.12f) % 1f
        figure.history.addFirst(figure.points.flatMap { listOf(it[0], it[1]) }.toFloatArray())
        while (figure.history.size > TRAIL_LENGTH) {
            figure.history.removeLast()
        }
    }

    private fun drawFigure(figure: Figure) {
        figure.history.forEachIndexed { index, points ->
            val age = index.toFloat() / TRAIL_LENGTH.toFloat()
            val alpha = (1f - age) * (1f - age) * 0.78f
            val color = colorFor((figure.colorPhase + figure.hueOffset + age * 0.15f) % 1f, alpha)
            val rounded = makeRoundedLoop(points)
            val buffer = makeBuffer(rounded)

            GLES30.glUniform4f(colorHandle, color[0], color[1], color[2], color[3])
            GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 0, buffer)
            GLES30.glEnableVertexAttribArray(positionHandle)
            GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, rounded.size / 2)
        }
    }

    private fun makeRoundedLoop(points: FloatArray): FloatArray {
        val output = ArrayList<Float>((POINT_COUNT * CURVE_SEGMENTS + 1) * 2)
        for (i in 0 until POINT_COUNT) {
            val prev = pointAt(points, i - 1)
            val current = pointAt(points, i)
            val next = pointAt(points, i + 1)
            val after = pointAt(points, i + 2)

            val c1 = floatArrayOf(
                current[0] + (next[0] - prev[0]) * SMOOTHNESS,
                current[1] + (next[1] - prev[1]) * SMOOTHNESS,
            )
            val c2 = floatArrayOf(
                next[0] - (after[0] - current[0]) * SMOOTHNESS,
                next[1] - (after[1] - current[1]) * SMOOTHNESS,
            )

            for (segment in 0 until CURVE_SEGMENTS) {
                val t = segment.toFloat() / CURVE_SEGMENTS.toFloat()
                val p = cubic(current, c1, c2, next, t)
                output.add(p[0])
                output.add(p[1])
            }
        }
        output.add(output[0])
        output.add(output[1])
        return output.toFloatArray()
    }

    private fun pointAt(points: FloatArray, index: Int): FloatArray {
        val normalized = ((index % POINT_COUNT) + POINT_COUNT) % POINT_COUNT
        return floatArrayOf(points[normalized * 2], points[normalized * 2 + 1])
    }

    private fun cubic(p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray, t: Float): FloatArray {
        val inv = 1f - t
        return floatArrayOf(
            inv * inv * inv * p0[0] + 3f * inv * inv * t * p1[0] + 3f * inv * t * t * p2[0] + t * t * t * p3[0],
            inv * inv * inv * p0[1] + 3f * inv * inv * t * p1[1] + 3f * inv * t * t * p2[1] + t * t * t * p3[1],
        )
    }

    private fun makeFigure(index: Int): Figure {
        val points = Array(POINT_COUNT) {
            floatArrayOf(randomSigned(0.8f), randomSigned(0.72f))
        }
        val velocities = Array(POINT_COUNT) {
            floatArrayOf(randomVelocity(), randomVelocity())
        }
        return Figure(points = points, velocities = velocities, colorPhase = index.toFloat() / FIGURE_COUNT.toFloat())
    }

    private fun randomVelocity(): Float {
        val value = 0.28f + random.nextFloat() * 0.54f
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
        private const val FIGURE_COUNT = 1
        private const val POINT_COUNT = 5
        private const val TRAIL_LENGTH = 42
        private const val CURVE_SEGMENTS = 18
        private const val SMOOTHNESS = 0.18f

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
