package com.example.screensaverwindows.renderer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.example.screensaverwindows.settings.RuntimeSettings
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class MazeRenderer : GLSurfaceView.Renderer {
    private val random = Random()
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)
    private val maze = Array(MAZE_SIZE) { Array(MAZE_SIZE) { Cell() } }
    private val visited = Array(MAZE_SIZE) { BooleanArray(MAZE_SIZE) }

    private lateinit var mesh: Mesh
    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var colorHandle = 0
    private var mvpHandle = 0
    private var lightHandle = 0
    private var width = 1
    private var height = 1
    private var lastFrameNanos = 0L
    private var current = GridPoint(0, 0)
    private var previous = GridPoint(0, 0)
    private var target = GridPoint(1, 0)
    private var progress = 0f
    private var heading = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.015f, 0.012f, 0.028f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES30.glGetAttribLocation(program, "aNormal")
        colorHandle = GLES30.glGetAttribLocation(program, "aColor")
        mvpHandle = GLES30.glGetUniformLocation(program, "uMvp")
        lightHandle = GLES30.glGetUniformLocation(program, "uLightDir")

        generateMaze(0, 0)
        mesh = buildMazeMesh()
        current = GridPoint(0, 0)
        previous = current
        target = chooseNextCell(current, previous)
        heading = angleTo(current, target)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = max(1, width)
        this.height = max(1, height)
        GLES30.glViewport(0, 0, this.width, this.height)
        val aspect = this.width.toFloat() / this.height.toFloat()
        Matrix.perspectiveM(projection, 0, 64f, aspect, 0.08f, 90f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val deltaSeconds = (if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((now - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.04f)
        }) * RuntimeSettings.speed
        lastFrameNanos = now
        updateCamera(deltaSeconds)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)
        GLES30.glUniform3f(lightHandle, -0.28f, 0.72f, 0.56f)

        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)

        mesh.buffer.position(0)
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, STRIDE_BYTES, mesh.buffer)
        GLES30.glEnableVertexAttribArray(positionHandle)
        mesh.buffer.position(3)
        GLES30.glVertexAttribPointer(normalHandle, 3, GLES30.GL_FLOAT, false, STRIDE_BYTES, mesh.buffer)
        GLES30.glEnableVertexAttribArray(normalHandle)
        mesh.buffer.position(6)
        GLES30.glVertexAttribPointer(colorHandle, 3, GLES30.GL_FLOAT, false, STRIDE_BYTES, mesh.buffer)
        GLES30.glEnableVertexAttribArray(colorHandle)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, mesh.vertexCount)
    }

    private fun updateCamera(deltaSeconds: Float) {
        progress += deltaSeconds * CAMERA_SPEED
        if (progress >= 1f) {
            previous = current
            current = target
            target = chooseNextCell(current, previous)
            progress = 0f
        }

        val smooth = progress * progress * (3f - 2f * progress)
        val from = cellCenter(current)
        val to = cellCenter(target)
        val x = from[0] + (to[0] - from[0]) * smooth
        val z = from[1] + (to[1] - from[1]) * smooth

        val targetHeading = angleTo(current, target)
        heading = lerpAngle(heading, targetHeading, deltaSeconds * 4.2f)
        val lookX = x + cos(heading) * 1.6f
        val lookZ = z + sin(heading) * 1.6f

        Matrix.setLookAtM(view, 0, x, EYE_HEIGHT, z, lookX, EYE_HEIGHT + 0.02f, lookZ, 0f, 1f, 0f)
    }

    private fun generateMaze(row: Int, col: Int) {
        visited[row][col] = true
        Direction.entries.shuffled(random).forEach { direction ->
            val nextRow = row + direction.rowDelta
            val nextCol = col + direction.colDelta
            if (nextRow in 0 until MAZE_SIZE && nextCol in 0 until MAZE_SIZE && !visited[nextRow][nextCol]) {
                maze[row][col].walls[direction.index] = false
                maze[nextRow][nextCol].walls[direction.opposite.index] = false
                generateMaze(nextRow, nextCol)
            }
        }
    }

    private fun chooseNextCell(cell: GridPoint, previousCell: GridPoint): GridPoint {
        val neighbors = Direction.entries
            .filter { !maze[cell.row][cell.col].walls[it.index] }
            .map { GridPoint(cell.row + it.rowDelta, cell.col + it.colDelta) }
            .filter { it.row in 0 until MAZE_SIZE && it.col in 0 until MAZE_SIZE }
        val forwardOptions = neighbors.filter { it != previousCell }
        val previousDirection = GridPoint(cell.row - previousCell.row, cell.col - previousCell.col)
        val turnOptions = forwardOptions.filter {
            it.row - cell.row != previousDirection.row || it.col - cell.col != previousDirection.col
        }
        val options = when {
            turnOptions.isNotEmpty() -> turnOptions
            forwardOptions.isNotEmpty() -> forwardOptions
            else -> neighbors
        }
        return options[random.nextInt(options.size)]
    }

    private fun buildMazeMesh(): Mesh {
        val values = mutableListOf<Float>()
        val half = MAZE_SIZE * CELL_SIZE * 0.5f
        val min = -half
        val max = half

        addQuad(values, min, 0f, min, max, 0f, min, max, 0f, max, min, 0f, max, FLOOR_COLOR)
        addQuad(values, min, WALL_HEIGHT, max, max, WALL_HEIGHT, max, max, WALL_HEIGHT, min, min, WALL_HEIGHT, min, CEILING_COLOR)

        for (row in 0 until MAZE_SIZE) {
            for (col in 0 until MAZE_SIZE) {
                val x0 = col * CELL_SIZE - half
                val x1 = x0 + CELL_SIZE
                val z0 = row * CELL_SIZE - half
                val z1 = z0 + CELL_SIZE
                val color = if ((row + col) % 2 == 0) WALL_COLOR_A else WALL_COLOR_B

                if (maze[row][col].walls[Direction.North.index]) addWall(values, x0, z0, x1, z0, color)
                if (maze[row][col].walls[Direction.West.index]) addWall(values, x0, z1, x0, z0, color)
                if (row == MAZE_SIZE - 1 && maze[row][col].walls[Direction.South.index]) addWall(values, x1, z1, x0, z1, color)
                if (col == MAZE_SIZE - 1 && maze[row][col].walls[Direction.East.index]) addWall(values, x1, z0, x1, z1, color)
            }
        }

        return Mesh(makeBuffer(values.toFloatArray()), values.size / FLOATS_PER_VERTEX)
    }

    private fun addWall(values: MutableList<Float>, x0: Float, z0: Float, x1: Float, z1: Float, color: FloatArray) {
        addQuad(values, x0, 0f, z0, x1, 0f, z1, x1, WALL_HEIGHT, z1, x0, WALL_HEIGHT, z0, color)
    }

    private fun addQuad(values: MutableList<Float>, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, color: FloatArray) {
        val normal = normalFor(ax, ay, az, bx, by, bz, cx, cy, cz)
        addVertex(values, ax, ay, az, normal, color)
        addVertex(values, bx, by, bz, normal, color)
        addVertex(values, cx, cy, cz, normal, color)
        addVertex(values, ax, ay, az, normal, color)
        addVertex(values, cx, cy, cz, normal, color)
        addVertex(values, dx, dy, dz, normal, color)
    }

    private fun normalFor(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float): FloatArray {
        val ux = bx - ax
        val uy = by - ay
        val uz = bz - az
        val vx = cx - ax
        val vy = cy - ay
        val vz = cz - az
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        val length = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
        return floatArrayOf(nx / length, ny / length, nz / length)
    }

    private fun addVertex(values: MutableList<Float>, x: Float, y: Float, z: Float, normal: FloatArray, color: FloatArray) {
        values.add(x)
        values.add(y)
        values.add(z)
        values.add(normal[0])
        values.add(normal[1])
        values.add(normal[2])
        values.add(color[0])
        values.add(color[1])
        values.add(color[2])
    }

    private fun cellCenter(point: GridPoint): FloatArray {
        val half = MAZE_SIZE * CELL_SIZE * 0.5f
        return floatArrayOf(point.col * CELL_SIZE - half + CELL_SIZE * 0.5f, point.row * CELL_SIZE - half + CELL_SIZE * 0.5f)
    }

    private fun angleTo(from: GridPoint, to: GridPoint): Float =
        atan2((to.row - from.row).toFloat(), (to.col - from.col).toFloat())

    private fun lerpAngle(from: Float, to: Float, amount: Float): Float {
        var delta = (to - from + Math.PI.toFloat()) % TWO_PI - Math.PI.toFloat()
        if (delta < -Math.PI.toFloat()) delta += TWO_PI
        return from + delta * amount.coerceIn(0f, 1f)
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
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values)
            position(0)
        }

    private data class Mesh(val buffer: FloatBuffer, val vertexCount: Int)
    private data class Cell(val walls: BooleanArray = booleanArrayOf(true, true, true, true))
    private data class GridPoint(val row: Int, val col: Int)

    private enum class Direction(val index: Int, val rowDelta: Int, val colDelta: Int) {
        North(0, -1, 0),
        East(1, 0, 1),
        South(2, 1, 0),
        West(3, 0, -1);

        val opposite: Direction
            get() = when (this) {
                North -> South
                East -> West
                South -> North
                West -> East
            }
    }

    private companion object {
        private const val MAZE_SIZE = 13
        private const val CELL_SIZE = 1.26f
        private const val WALL_HEIGHT = 1.82f
        private const val EYE_HEIGHT = 0.82f
        private const val CAMERA_SPEED = 1.05f
        private const val FLOATS_PER_VERTEX = 9
        private const val STRIDE_BYTES = FLOATS_PER_VERTEX * Float.SIZE_BYTES
        private const val TWO_PI = (Math.PI * 2.0).toFloat()

        private val WALL_COLOR_A = floatArrayOf(1.0f, 0.0f, 0.0f)
        private val WALL_COLOR_B = floatArrayOf(0.95f, 0.0f, 0.0f)
        private val FLOOR_COLOR = floatArrayOf(0.0f, 1.0f, 0.0f)
        private val CEILING_COLOR = floatArrayOf(0.0f, 0.0f, 1.0f)

        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying vec3 vWorldPosition;

            void main() {
                vNormal = normalize(aNormal);
                vColor = aColor;
                vWorldPosition = aPosition;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uLightDir;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying vec3 vWorldPosition;

            float brickMortar(vec2 uv) {
                vec2 brickSize = vec2(0.68, 0.34);
                float row = floor(uv.y / brickSize.y);
                uv.x += mod(row, 2.0) * brickSize.x * 0.5;
                vec2 cell = fract(uv / brickSize);
                vec2 rounded = abs(cell - 0.5) - vec2(0.42, 0.34);
                float roundedRect = length(max(rounded, 0.0)) + min(max(rounded.x, rounded.y), 0.0) - 0.055;
                float brickShape = 1.0 - smoothstep(0.0, 0.025, roundedRect);
                return 1.0 - brickShape;
            }

            float tileGrout(vec2 uv, float scale) {
                vec2 cell = fract(uv / vec2(scale));
                return clamp(
                    step(cell.x, 0.035) + step(0.965, cell.x) +
                    step(cell.y, 0.035) + step(0.965, cell.y),
                    0.0,
                    1.0
                );
            }

            vec3 psxQuantize(vec3 color) {
                return floor(color * 7.0) / 7.0;
            }

            void main() {
                vec3 normal = normalize(vNormal);
                float diffuse = max(dot(normal, normalize(uLightDir)), 0.0);
                vec3 baseColor = vColor;

                if (vColor.r > 0.80 && vColor.g < 0.20) {
                    vec2 brickUv = abs(normal.z) > abs(normal.x)
                        ? vec2(vWorldPosition.x, vWorldPosition.y)
                        : vec2(vWorldPosition.z, vWorldPosition.y);
                    float mortar = brickMortar(brickUv);
                    float brickNoise = fract(sin(dot(floor(brickUv * vec2(2.1, 4.2)), vec2(12.9898, 78.233))) * 43758.5453);
                    vec3 brick = mix(vec3(0.40, 0.006, 0.010), vec3(0.48, 0.008, 0.016), brickNoise);
                    vec3 mortarColor = vec3(0.906, 0.851, 0.831);
                    baseColor = mix(brick, mortarColor, mortar);
                } else if (vColor.g > 0.80) {
                    baseColor = vec3(1.0, 0.647, 0.0);
                } else if (vColor.b > 0.80) {
                    float grout = tileGrout(vWorldPosition.xz, 0.34);
                    float tileNoise = fract(sin(dot(floor(vWorldPosition.xz / vec2(0.34)), vec2(43.17, 19.91))) * 24634.6345);
                    vec3 tile = mix(vec3(0.78, 0.78, 0.78), vec3(0.894, 0.894, 0.894), step(0.5, tileNoise));
                    vec3 groutColor = vec3(0.58, 0.58, 0.58);
                    baseColor = mix(tile, groutColor, grout);
                }

                float ambient = vColor.r > 0.80 ? 0.38 : 0.34;
                float light = vColor.r > 0.80 || vColor.g > 0.80
                    ? 1.0
                    : floor((ambient + diffuse * 0.58) * 3.0) / 3.0;
                vec3 color = psxQuantize(baseColor * light);
                gl_FragColor = vec4(color, 1.0);
            }
        """
    }
}
