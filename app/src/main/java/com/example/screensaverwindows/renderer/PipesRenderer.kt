package com.example.screensaverwindows.renderer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class PipesRenderer : GLSurfaceView.Renderer {
    private val random = Random()
    private val segments = ArrayDeque<PipeSegment>()
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val modelView = FloatArray(16)
    private val modelViewProjection = FloatArray(16)
    private val currentCell = IntArray(3)

    private lateinit var cylinderMesh: Mesh
    private lateinit var sphereMesh: Mesh
    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var colorHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var lightHandle = 0
    private var lastBuildMs = 0L
    private var startMs = 0L
    private var width = 1
    private var height = 1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.006f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES30.glGetAttribLocation(program, "aNormal")
        colorHandle = GLES30.glGetUniformLocation(program, "uColor")
        mvpHandle = GLES30.glGetUniformLocation(program, "uMvp")
        modelHandle = GLES30.glGetUniformLocation(program, "uModel")
        lightHandle = GLES30.glGetUniformLocation(program, "uLightDir")
        cylinderMesh = makeCylinderMesh(CYLINDER_SIDES)
        sphereMesh = makeSphereMesh(SPHERE_COLUMNS, SPHERE_ROWS)
        startMs = System.currentTimeMillis()
        resetPath()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = max(1, width)
        this.height = max(1, height)
        GLES30.glViewport(0, 0, this.width, this.height)
        val aspect = this.width.toFloat() / this.height.toFloat()
        Matrix.perspectiveM(projection, 0, 54f, aspect, 0.1f, 120f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.currentTimeMillis()
        if (now - lastBuildMs > 64L) {
            addSegment()
            lastBuildMs = now
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)
        GLES30.glUniform3f(lightHandle, -0.32f, 0.78f, 0.82f)

        val elapsed = (now - startMs) / 1000f
        Matrix.setLookAtM(
            view,
            0,
            0f,
            0.25f,
            15.2f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        )
        Matrix.rotateM(view, 0, elapsed * 7.2f, 0f, 1f, 0f)
        Matrix.rotateM(view, 0, 9f + sin(elapsed * 0.55f) * 5f, 1f, 0f, 0f)

        segments.forEach { drawSegment(it) }
    }

    private fun resetPath() {
        segments.clear()
        currentCell[0] = 0
        currentCell[1] = 0
        currentCell[2] = 0
        repeat(56) { addSegment() }
    }

    private fun addSegment() {
        val axis = random.nextInt(3)
        val direction = if (random.nextBoolean()) 1 else -1
        val start = currentCell.copyOf()
        currentCell[axis] = (currentCell[axis] + direction).coerceIn(-GRID_LIMIT, GRID_LIMIT)
        if (start[axis] == currentCell[axis]) {
            currentCell[axis] = -currentCell[axis]
        }
        val end = currentCell.copyOf()
        segments.addLast(PipeSegment(start, end, axis, PALETTE[random.nextInt(PALETTE.size)]))
        while (segments.size > 112) {
            segments.removeFirst()
        }
    }

    private fun drawSegment(segment: PipeSegment) {
        val sx = segment.start[0] * CELL
        val sy = segment.start[1] * CELL
        val sz = segment.start[2] * CELL
        val ex = segment.end[0] * CELL
        val ey = segment.end[1] * CELL
        val ez = segment.end[2] * CELL

        val cx = (sx + ex) * 0.5f
        val cy = (sy + ey) * 0.5f
        val cz = (sz + ez) * 0.5f
        val length = max(CELL, max(abs(ex - sx), max(abs(ey - sy), abs(ez - sz))) + PIPE_RADIUS * 1.25f)

        drawCylinder(cx, cy, cz, segment.axis, length, segment.color, 0.96f)
        drawSphere(ex, ey, ez, segment.color, 1f)
    }

    private fun drawCylinder(
        x: Float,
        y: Float,
        z: Float,
        axis: Int,
        length: Float,
        color: FloatArray,
        alpha: Float,
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        when (axis) {
            1 -> Matrix.rotateM(model, 0, 90f, 0f, 0f, 1f)
            2 -> Matrix.rotateM(model, 0, -90f, 0f, 1f, 0f)
        }
        Matrix.scaleM(model, 0, length, PIPE_RADIUS, PIPE_RADIUS)
        drawMesh(cylinderMesh, color, alpha)
    }

    private fun drawSphere(x: Float, y: Float, z: Float, color: FloatArray, alpha: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.scaleM(model, 0, JOINT_RADIUS, JOINT_RADIUS, JOINT_RADIUS)
        drawMesh(sphereMesh, color, alpha)
    }

    private fun drawMesh(mesh: Mesh, color: FloatArray, alpha: Float) {
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        Matrix.multiplyMM(modelViewProjection, 0, projection, 0, modelView, 0)

        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, modelViewProjection, 0)
        GLES30.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        GLES30.glUniform4f(colorHandle, color[0], color[1], color[2], alpha)

        mesh.buffer.position(0)
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, STRIDE_BYTES, mesh.buffer)
        GLES30.glEnableVertexAttribArray(positionHandle)
        mesh.buffer.position(3)
        GLES30.glVertexAttribPointer(normalHandle, 3, GLES30.GL_FLOAT, false, STRIDE_BYTES, mesh.buffer)
        GLES30.glEnableVertexAttribArray(normalHandle)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, mesh.vertexCount)
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

    private fun makeCylinderMesh(sides: Int): Mesh {
        val values = mutableListOf<Float>()
        for (i in 0 until sides) {
            val a0 = i.toFloat() / sides.toFloat() * TWO_PI
            val a1 = (i + 1).toFloat() / sides.toFloat() * TWO_PI
            val y0 = cos(a0)
            val z0 = sin(a0)
            val y1 = cos(a1)
            val z1 = sin(a1)

            addVertex(values, -0.5f, y0, z0, 0f, y0, z0)
            addVertex(values, 0.5f, y0, z0, 0f, y0, z0)
            addVertex(values, 0.5f, y1, z1, 0f, y1, z1)
            addVertex(values, -0.5f, y0, z0, 0f, y0, z0)
            addVertex(values, 0.5f, y1, z1, 0f, y1, z1)
            addVertex(values, -0.5f, y1, z1, 0f, y1, z1)

            addVertex(values, -0.5f, 0f, 0f, -1f, 0f, 0f)
            addVertex(values, -0.5f, y1, z1, -1f, 0f, 0f)
            addVertex(values, -0.5f, y0, z0, -1f, 0f, 0f)

            addVertex(values, 0.5f, 0f, 0f, 1f, 0f, 0f)
            addVertex(values, 0.5f, y0, z0, 1f, 0f, 0f)
            addVertex(values, 0.5f, y1, z1, 1f, 0f, 0f)
        }
        return Mesh(makeBuffer(values.toFloatArray()), values.size / FLOATS_PER_VERTEX)
    }

    private fun makeSphereMesh(columns: Int, rows: Int): Mesh {
        val values = mutableListOf<Float>()
        for (row in 0 until rows) {
            val v0 = row.toFloat() / rows.toFloat()
            val v1 = (row + 1).toFloat() / rows.toFloat()
            val theta0 = -PI.toFloat() * 0.5f + v0 * PI.toFloat()
            val theta1 = -PI.toFloat() * 0.5f + v1 * PI.toFloat()

            for (column in 0 until columns) {
                val u0 = column.toFloat() / columns.toFloat()
                val u1 = (column + 1).toFloat() / columns.toFloat()
                val phi0 = u0 * TWO_PI
                val phi1 = u1 * TWO_PI

                val p00 = spherePoint(theta0, phi0)
                val p10 = spherePoint(theta0, phi1)
                val p01 = spherePoint(theta1, phi0)
                val p11 = spherePoint(theta1, phi1)

                addVertex(values, p00)
                addVertex(values, p01)
                addVertex(values, p11)
                addVertex(values, p00)
                addVertex(values, p11)
                addVertex(values, p10)
            }
        }
        return Mesh(makeBuffer(values.toFloatArray()), values.size / FLOATS_PER_VERTEX)
    }

    private fun spherePoint(theta: Float, phi: Float): FloatArray {
        val y = sin(theta)
        val radius = cos(theta)
        val x = radius * cos(phi)
        val z = radius * sin(phi)
        return floatArrayOf(x, y, z)
    }

    private fun addVertex(values: MutableList<Float>, point: FloatArray) {
        addVertex(values, point[0], point[1], point[2], point[0], point[1], point[2])
    }

    private fun addVertex(
        values: MutableList<Float>,
        x: Float,
        y: Float,
        z: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
    ) {
        values.add(x)
        values.add(y)
        values.add(z)
        values.add(normalX)
        values.add(normalY)
        values.add(normalZ)
    }

    private data class Mesh(
        val buffer: FloatBuffer,
        val vertexCount: Int,
    )

    private data class PipeSegment(
        val start: IntArray,
        val end: IntArray,
        val axis: Int,
        val color: FloatArray,
    )

    private companion object {
        private const val CELL = 1.34f
        private const val GRID_LIMIT = 5
        private const val PIPE_RADIUS = 0.27f
        private const val JOINT_RADIUS = 0.41f
        private const val CYLINDER_SIDES = 28
        private const val SPHERE_COLUMNS = 24
        private const val SPHERE_ROWS = 14
        private const val FLOATS_PER_VERTEX = 6
        private const val STRIDE_BYTES = FLOATS_PER_VERTEX * Float.SIZE_BYTES
        private const val TWO_PI = (PI * 2.0).toFloat()

        private val PALETTE = arrayOf(
            floatArrayOf(0.0f, 1.0f, 1.0f, 1f),
            floatArrayOf(1.0f, 0.92f, 0.05f, 1f),
            floatArrayOf(1.0f, 0.04f, 0.38f, 1f),
            floatArrayOf(0.02f, 1.0f, 0.20f, 1f),
            floatArrayOf(0.42f, 0.18f, 1.0f, 1f),
            floatArrayOf(1.0f, 0.34f, 0.0f, 1f),
            floatArrayOf(0.0f, 0.42f, 1.0f, 1f),
        )

        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vPosition;

            void main() {
                vec4 worldPosition = uModel * vec4(aPosition, 1.0);
                vPosition = worldPosition.xyz;
                vNormal = normalize(mat3(uModel) * aNormal);
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uLightDir;
            varying vec3 vNormal;
            varying vec3 vPosition;

            void main() {
                vec3 normal = normalize(vNormal);
                float diffuse = max(dot(normal, normalize(uLightDir)), 0.0);
                float rim = pow(1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0), 2.35);
                float gloss = pow(max(dot(reflect(-normalize(uLightDir), normal), vec3(0.0, 0.0, 1.0)), 0.0), 20.0);
                vec3 highlight = vec3(1.0, 0.98, 0.86) * (rim * 0.45 + gloss * 0.72);
                vec3 color = uColor.rgb * (0.10 + diffuse * 1.08) + highlight;
                gl_FragColor = vec4(color, uColor.a);
            }
        """
    }
}
