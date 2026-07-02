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

class BubblesRenderer : GLSurfaceView.Renderer {
    private val random = Random()
    private val bubbles = Array(BUBBLE_COUNT) { makeBubble(it) }
    private lateinit var quadBuffer: FloatBuffer
    private var program = 0
    private var backgroundProgram = 0
    private var positionHandle = 0
    private var backgroundPositionHandle = 0
    private var centerHandle = 0
    private var radiusHandle = 0
    private var colorHandle = 0
    private var aspectHandle = 0
    private var width = 1
    private var height = 1
    private var aspect = 1f
    private var lastFrameNanos = 0L
    private var startNanos = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.015f, 0.02f, 0.035f, 1f)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        backgroundProgram = buildProgram(BACKGROUND_VERTEX_SHADER, BACKGROUND_FRAGMENT_SHADER)
        backgroundPositionHandle = GLES30.glGetAttribLocation(backgroundProgram, "aPosition")

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        centerHandle = GLES30.glGetUniformLocation(program, "uCenter")
        radiusHandle = GLES30.glGetUniformLocation(program, "uRadius")
        colorHandle = GLES30.glGetUniformLocation(program, "uColor")
        aspectHandle = GLES30.glGetUniformLocation(program, "uAspect")
        quadBuffer = makeBuffer(QUAD_VERTICES)
        startNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = max(1, width)
        this.height = max(1, height)
        aspect = this.width.toFloat() / this.height.toFloat()
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
        drawBackground()

        val elapsed = (now - startNanos) / 1_000_000_000f
        bubbles.forEach { bubble ->
            updateBubble(bubble, deltaSeconds, elapsed)
        }
        resolveCollisions()

        GLES30.glUseProgram(program)
        GLES30.glUniform1f(aspectHandle, aspect)
        bubbles.forEach { bubble -> drawBubble(bubble, elapsed) }
    }

    private fun drawBackground() {
        GLES30.glUseProgram(backgroundProgram)
        quadBuffer.position(0)
        GLES30.glVertexAttribPointer(backgroundPositionHandle, 2, GLES30.GL_FLOAT, false, 0, quadBuffer)
        GLES30.glEnableVertexAttribArray(backgroundPositionHandle)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, QUAD_VERTEX_COUNT)
    }

    private fun updateBubble(bubble: Bubble, deltaSeconds: Float, elapsed: Float) {
        if (elapsed < bubble.spawnDelay) {
            bubble.x = SPAWN_X
            bubble.y = SPAWN_Y
            return
        }

        if (!bubble.active) {
            bubble.active = true
            bubble.x = SPAWN_X + random.nextFloat() * 0.03f
            bubble.y = SPAWN_Y + random.nextFloat() * 0.03f
        }

        bubble.x += bubble.velocityX * deltaSeconds
        bubble.y += bubble.velocityY * deltaSeconds

        val radius = BUBBLE_RADIUS
        val radiusX = radius / aspect
        if (bubble.x - radiusX < -1f || bubble.x + radiusX > 1f) {
            bubble.x = bubble.x.coerceIn(-1f + radiusX, 1f - radiusX)
            bubble.velocityX = -bubble.velocityX
        }
        if (bubble.y - radius < -1f || bubble.y + radius > 1f) {
            bubble.y = bubble.y.coerceIn(-1f + radius, 1f - radius)
            bubble.velocityY = -bubble.velocityY
        }
    }

    private fun resolveCollisions() {
        val minDistanceY = BUBBLE_RADIUS * 2.04f
        val minDistanceX = minDistanceY / aspect
        for (i in bubbles.indices) {
            val a = bubbles[i]
            if (!a.active || a.isInSpawnZone()) continue
            for (j in i + 1 until bubbles.size) {
                val b = bubbles[j]
                if (!b.active || b.isInSpawnZone()) continue
                val dx = (b.x - a.x) / minDistanceX
                val dy = (b.y - a.y) / minDistanceY
                val distance = sqrt(dx * dx + dy * dy)
                if (distance in 0.001f..1f) {
                    val overlap = (1f - distance) * 0.5f
                    val nx = dx / distance
                    val ny = dy / distance
                    a.x -= nx * overlap * minDistanceX
                    b.x += nx * overlap * minDistanceX
                    a.y -= ny * overlap * minDistanceY
                    b.y += ny * overlap * minDistanceY

                    val avx = a.velocityX
                    val avy = a.velocityY
                    a.velocityX = b.velocityX
                    a.velocityY = b.velocityY
                    b.velocityX = avx
                    b.velocityY = avy
                }
            }
        }
    }

    private fun drawBubble(bubble: Bubble, elapsed: Float) {
        if (elapsed < bubble.spawnDelay) return
        val color = colorFor((bubble.colorPhase + elapsed * bubble.colorSpeed) % 1f)
        GLES30.glUniform2f(centerHandle, bubble.x, bubble.y)
        GLES30.glUniform1f(radiusHandle, BUBBLE_RADIUS)
        GLES30.glUniform4f(colorHandle, color[0], color[1], color[2], bubble.alpha)

        quadBuffer.position(0)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 0, quadBuffer)
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, QUAD_VERTEX_COUNT)
    }

    private fun makeBubble(index: Int): Bubble {
        val angle = 0.32f + random.nextFloat() * 0.95f
        val speed = 0.66f + random.nextFloat() * 0.32f
        return Bubble(
            x = SPAWN_X,
            y = SPAWN_Y,
            velocityX = cos(angle) * speed / 1.6f,
            velocityY = sin(angle) * speed,
            colorPhase = random.nextFloat(),
            colorSpeed = 0.035f + random.nextFloat() * 0.045f,
            alpha = 0.48f + random.nextFloat() * 0.16f,
            spawnDelay = index * SPAWN_INTERVAL,
        )
    }

    private fun colorFor(hue: Float): FloatArray {
        val h = (hue * 6f).toInt()
        val f = hue * 6f - h
        val q = 1f - f
        return when (h % 6) {
            0 -> floatArrayOf(1f, f, 0f)
            1 -> floatArrayOf(q, 1f, 0f)
            2 -> floatArrayOf(0f, 1f, f)
            3 -> floatArrayOf(0f, q, 1f)
            4 -> floatArrayOf(f, 0f, 1f)
            else -> floatArrayOf(1f, 0f, q)
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

    private data class Bubble(
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        val colorPhase: Float,
        val colorSpeed: Float,
        val alpha: Float,
        val spawnDelay: Float,
        var active: Boolean = false,
    ) {
        fun isInSpawnZone(): Boolean =
            x < SPAWN_X + BUBBLE_RADIUS * 1.2f && y < SPAWN_Y + BUBBLE_RADIUS * 1.2f
    }

    private companion object {
        private const val BUBBLE_COUNT = 18
        private const val BUBBLE_RADIUS = 0.17f
        private const val SPAWN_X = -0.92f
        private const val SPAWN_Y = -0.82f
        private const val SPAWN_INTERVAL = 0.42f
        private const val QUAD_VERTEX_COUNT = 6

        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            1f, 1f,
            -1f, -1f,
            1f, 1f,
            -1f, 1f,
        )

        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            uniform vec2 uCenter;
            uniform float uRadius;
            uniform float uAspect;
            varying vec2 vLocal;

            void main() {
                vLocal = aPosition;
                vec2 scale = vec2(uRadius / uAspect, uRadius);
                gl_Position = vec4(uCenter + aPosition * scale, 0.0, 1.0);
            }
        """

        private const val BACKGROUND_VERTEX_SHADER = """
            attribute vec2 aPosition;
            varying vec2 vUv;

            void main() {
                vUv = aPosition * 0.5 + 0.5;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val BACKGROUND_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vUv;

            float band(vec2 uv, float offset, float width, float slope) {
                float y = 0.22 + sin((uv.x + offset) * 3.14159) * 0.16 + uv.x * slope;
                return 1.0 - smoothstep(0.0, width, abs(uv.y - y));
            }

            void main() {
                vec2 uv = vUv;
                vec3 top = vec3(0.055, 0.145, 0.300);
                vec3 bottom = vec3(0.015, 0.030, 0.075);
                vec3 color = mix(bottom, top, smoothstep(0.0, 1.0, uv.y));

                float greenBand = band(uv, 0.05, 0.34, 0.16);
                float cyanBand = band(uv, 0.38, 0.28, -0.08);
                float amberBand = band(uv, -0.18, 0.22, 0.04);
                float glow = smoothstep(0.0, 0.95, 1.0 - distance(uv, vec2(0.58, 0.42)));

                color += vec3(0.18, 0.58, 0.30) * greenBand * 0.36;
                color += vec3(0.05, 0.38, 0.72) * cyanBand * 0.33;
                color += vec3(0.78, 0.42, 0.12) * amberBand * 0.18;
                color += vec3(0.08, 0.18, 0.32) * glow * 0.28;

                float vignette = smoothstep(0.92, 0.18, distance(uv, vec2(0.5, 0.5)));
                color *= 0.52 + vignette * 0.48;
                gl_FragColor = vec4(color, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            varying vec2 vLocal;

            void main() {
                vec2 p = vLocal;
                float dist = length(p);
                if (dist > 1.0) {
                    discard;
                }

                float rimOuter = smoothstep(0.66, 1.0, dist);
                float rimSoft = smoothstep(0.26, 1.0, dist);
                float rim = rimOuter * 0.82 + rimSoft * 0.18;
                float centerFade = smoothstep(0.0, 1.0, dist);
                vec2 highlightPoint = vec2(-0.38, 0.42);
                float highlight = 1.0 - smoothstep(0.0, 0.18, length(p - highlightPoint));
                vec2 smallHighlightPoint = vec2(-0.18, 0.58);
                float smallHighlight = 1.0 - smoothstep(0.0, 0.08, length(p - smallHighlightPoint));

                vec3 inner = uColor.rgb * 0.16;
                vec3 shell = uColor.rgb * (0.18 + centerFade * 0.32);
                vec3 color = mix(inner, shell, smoothstep(0.0, 1.0, dist)) + uColor.rgb * rim * 0.72 + vec3(1.0) * (highlight * 0.82 + smallHighlight * 0.48);
                float alpha = uColor.a * (0.08 + rim * 0.66 + highlight * 0.26 + smallHighlight * 0.16);
                gl_FragColor = vec4(color, alpha);
            }
        """
    }
}
