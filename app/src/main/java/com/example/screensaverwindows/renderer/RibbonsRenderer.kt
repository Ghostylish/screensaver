package com.example.screensaverwindows.renderer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class RibbonsRenderer : GLSurfaceView.Renderer {
    private val random = Random()
    private val ribbons = List(RIBBON_COUNT) { makeRibbon(it) }
    private var program = 0
    private var positionHandle = 0
    private var alphaHandle = 0
    private var colorHandle = 0
    private var width = 1
    private var height = 1
    private var lastFrameNanos = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0.008f, 1f)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        alphaHandle = GLES30.glGetAttribLocation(program, "aAlpha")
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

        val aspect = width.toFloat() / height.toFloat()
        ribbons.forEach { ribbon ->
            updateRibbon(ribbon, deltaSeconds, aspect)
            drawRibbon(ribbon, aspect)
        }
    }

    private fun updateRibbon(ribbon: Ribbon, deltaSeconds: Float, aspect: Float) {
        ribbon.phase += deltaSeconds * ribbon.waveSpeed
        ribbon.chaosPhase += deltaSeconds * ribbon.chaosSpeed
        val turnX = cos(ribbon.phase) +
            sin(ribbon.chaosPhase * 1.73f + ribbon.phaseOffset) * 0.82f +
            cos((ribbon.phase + ribbon.chaosPhase) * 0.47f) * 0.55f
        val turnY = sin(ribbon.phase * 0.83f + ribbon.phaseOffset) +
            cos(ribbon.chaosPhase * 1.41f) * 0.88f +
            sin((ribbon.phase - ribbon.chaosPhase) * 0.62f) * 0.52f
        ribbon.velocityX += turnX * ribbon.turnRate * deltaSeconds
        ribbon.velocityY += turnY * ribbon.turnRate * deltaSeconds

        val speed = sqrt(ribbon.velocityX * ribbon.velocityX + ribbon.velocityY * ribbon.velocityY)
        if (speed > ribbon.maxSpeed) {
            val scale = ribbon.maxSpeed / speed
            ribbon.velocityX *= scale
            ribbon.velocityY *= scale
        }

        ribbon.x += ribbon.velocityX * deltaSeconds
        ribbon.y += ribbon.velocityY * deltaSeconds

        val marginX = aspect + 0.24f
        if (ribbon.x < -marginX || ribbon.x > marginX) {
            ribbon.x = ribbon.x.coerceIn(-marginX, marginX)
            ribbon.velocityX = -ribbon.velocityX
        }
        if (ribbon.y < -1.16f || ribbon.y > 1.16f) {
            ribbon.y = ribbon.y.coerceIn(-1.16f, 1.16f)
            ribbon.velocityY = -ribbon.velocityY
        }

        ribbon.hue = (ribbon.hue + deltaSeconds * 0.035f) % 1f
        ribbon.points.addFirst(floatArrayOf(ribbon.x, ribbon.y))
        while (ribbon.points.size > TRAIL_LENGTH) {
            ribbon.points.removeLast()
        }
    }

    private fun drawRibbon(ribbon: Ribbon, aspect: Float) {
        if (ribbon.points.size < 3) return

        val vertices = ArrayList<Float>(ribbon.points.size * 6)
        val points = ribbon.points.toList()
        for (i in points.indices) {
            val previous = points.getOrElse(i - 1) { points[i] }
            val next = points.getOrElse(i + 1) { points[i] }
            val dx = next[0] - previous[0]
            val dy = next[1] - previous[1]
            val length = max(0.001f, sqrt(dx * dx + dy * dy))
            val normalX = -dy / length / aspect
            val normalY = dx / length
            val age = i.toFloat() / (points.size - 1).toFloat()
            val center = sin((1f - age) * 3.14159f).coerceAtLeast(0.08f)
            val taper = center * (1f + center * 3f)
            val halfWidth = ribbon.width * taper
            val fade = 1f - age
            val alpha = fade * fade * fade * ribbon.alpha

            vertices.add(points[i][0] + normalX * halfWidth)
            vertices.add(points[i][1] + normalY * halfWidth)
            vertices.add(alpha)
            vertices.add(points[i][0] - normalX * halfWidth)
            vertices.add(points[i][1] - normalY * halfWidth)
            vertices.add(alpha * 0.78f)
        }

        val buffer = makeBuffer(vertices.toFloatArray())
        val stride = 3 * Float.SIZE_BYTES
        val color = colorFor(ribbon.hue)
        GLES30.glUniform4f(colorHandle, color[0], color[1], color[2], 1f)

        buffer.position(0)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, stride, buffer)
        GLES30.glEnableVertexAttribArray(positionHandle)
        buffer.position(2)
        GLES30.glVertexAttribPointer(alphaHandle, 1, GLES30.GL_FLOAT, false, stride, buffer)
        GLES30.glEnableVertexAttribArray(alphaHandle)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, vertices.size / 3)
    }

    private fun makeRibbon(index: Int): Ribbon {
        val angle = random.nextFloat() * 6.28318f
        val speed = 0.72f + random.nextFloat() * 0.54f
        val ribbon = Ribbon(
            x = randomSigned(1.2f),
            y = randomSigned(0.76f),
            velocityX = cos(angle) * speed,
            velocityY = sin(angle) * speed,
            hue = index.toFloat() / RIBBON_COUNT.toFloat(),
            phase = random.nextFloat() * 6.28318f,
            chaosPhase = random.nextFloat() * 6.28318f,
            phaseOffset = random.nextFloat() * 6.28318f,
            waveSpeed = 1.45f + random.nextFloat() * 1.35f,
            chaosSpeed = 1.9f + random.nextFloat() * 2.2f,
            turnRate = 0.84f + random.nextFloat() * 0.76f,
            maxSpeed = 1.26f + random.nextFloat() * 0.48f,
            width = 0.032f + random.nextFloat() * 0.026f,
            alpha = 0.72f + random.nextFloat() * 0.24f,
        )
        repeat(TRAIL_LENGTH / 2) {
            ribbon.points.addLast(
                floatArrayOf(
                    ribbon.x - ribbon.velocityX * it * 0.018f,
                    ribbon.y - ribbon.velocityY * it * 0.018f,
                ),
            )
        }
        return ribbon
    }

    private fun randomSigned(scale: Float): Float =
        (random.nextFloat() * 2f - 1f) * scale

    private fun colorFor(hue: Float): FloatArray {
        val h = (hue * 6f).toInt()
        val f = hue * 6f - h
        val q = 1f - f
        return when (h % 6) {
            0 -> floatArrayOf(1f, f * 0.82f, 0f)
            1 -> floatArrayOf(q * 0.82f, 1f, 0f)
            2 -> floatArrayOf(0f, 1f, f * 0.9f)
            3 -> floatArrayOf(0f, q * 0.86f, 1f)
            4 -> floatArrayOf(f * 0.9f, 0f, 1f)
            else -> floatArrayOf(1f, 0f, q * 0.86f)
        }
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

    private data class Ribbon(
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        var hue: Float,
        var phase: Float,
        var chaosPhase: Float,
        val phaseOffset: Float,
        val waveSpeed: Float,
        val chaosSpeed: Float,
        val turnRate: Float,
        val maxSpeed: Float,
        val width: Float,
        val alpha: Float,
        val points: ArrayDeque<FloatArray> = ArrayDeque(),
    )

    private companion object {
        private const val RIBBON_COUNT = 6
        private const val TRAIL_LENGTH = 240

        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute float aAlpha;
            varying float vAlpha;

            void main() {
                vAlpha = aAlpha;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            varying float vAlpha;

            void main() {
                gl_FragColor = vec4(uColor.rgb, uColor.a * vAlpha);
            }
        """
    }
}
