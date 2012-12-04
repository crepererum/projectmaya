import akka.actor.Actor
import akka.actor.ActorPath
import akka.actor.Cancellable

import de.matthiasmann.twl.utils.PNGDecoder

import java.nio.ByteBuffer

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ContextAttribs
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.DisplayMode
import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL13._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL30._
import org.lwjgl.opengl.PixelFormat
import org.lwjgl.util.glu.GLU._
import org.lwjgl.util.vector.Matrix4f
import org.lwjgl.util.vector.Vector2f
import org.lwjgl.util.vector.Vector3f

import scala.collection.mutable.HashMap
import scala.concurrent.duration._
import scala.io.Source

case object showYourTile

case class Tile(x: Double, y: Double, z: Integer, r: Double, h: Double, w: Double, id: String)
case object RemoveTile

class Gui extends Actor {
	val Tick = "tick"
	val textures = HashMap.empty[String, Integer]
	val tiles = HashMap.empty[ActorPath, Tile]
	val dimHeight = 600
	val dimWidh = 800
	val projectionMatrix = buildProjectionMatrix
	var schedulerCancellable: Option[Cancellable] = None
	var programId = 0
	var shaderFragmentId = 0
	var shaderVertexId = 0
	var uniformIdMatrix = -1
	var uniformIdTexture = -1
	var vertexArrayId = 0
	var vertexBufferId = 0

	def receive = {
		case Tick => {
			if (Display.isCloseRequested()) {
				context.system.shutdown
			} else {
				redraw
				context.actorSelection("../World/*") ! showYourTile
			}
		}
		case tile: Tile => {
			try {
				if (!textures.contains(tile.id)) {
					textures += (tile.id -> loadPNGTexture(tile.id))
				}

				tiles += (sender.path -> tile)
			} catch {
				case e: IllegalArgumentException => {
					val dummyTile = tile.copy(id = "dummy")

					if (!textures.contains(dummyTile.id)) {
						textures += (dummyTile.id -> loadPNGTexture(dummyTile.id))
					}

					tiles += (sender.path -> dummyTile)
				}
			}
		}
		case RemoveTile => {
			tiles -= sender.path
		}
	}

	override def preStart() {
		// OpenGL pixel format
		val pixelFormat = new PixelFormat

		// OpenGL context
		val contextAttribs = new ContextAttribs(3, 0)
		contextAttribs.withForwardCompatible(true)

		// init OpenGL
		Display.setDisplayMode(new DisplayMode(dimWidh, dimHeight))
		Display.setTitle("Project Maya")
		Display.create(pixelFormat, contextAttribs)

		// enable some stuff
		glEnable(GL_BLEND)
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

		// set viewport
		glViewport(0, 0, dimWidh, dimHeight)

		// setup vertex data (2 triangles)
		val vertices = Array[Float](
			-.5f, +.5f, 0f,
			-.5f, -.5f, 0f,
			+.5f, -.5f, 0f,

			+.5f, -.5f, 0f,
			+.5f, +.5f, 0f,
			-.5f, +.5f, 0f)

		val verticesBuffer = BufferUtils.createFloatBuffer(vertices.length)
		verticesBuffer.put(vertices)
		verticesBuffer.flip()

		vertexArrayId = glGenVertexArrays()
		glBindVertexArray(vertexArrayId)

		vertexBufferId = glGenBuffers()
		glBindBuffer(GL_ARRAY_BUFFER, vertexBufferId)
		glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW)

		// setup shader
		shaderVertexId = loadShader("vertex", GL_VERTEX_SHADER)
		shaderFragmentId = loadShader("fragment", GL_FRAGMENT_SHADER)
		programId = glCreateProgram()
		glAttachShader(programId, shaderVertexId)
		glAttachShader(programId, shaderFragmentId)
		glLinkProgram(programId)
		glValidateProgram(programId)
		uniformIdMatrix = glGetUniformLocation(programId, "MVP")
		uniformIdTexture = glGetUniformLocation(programId, "myTextureSampler")

		checkGLError

		// setup ticks
		val sys = context.system
		import sys.dispatcher
		schedulerCancellable = Some(context.system.scheduler.schedule(100.milliseconds, 100.milliseconds, self, Tick))

	}

	override def postStop() {
		// unload scheduler
		schedulerCancellable match {
			case Some(cancellable) => cancellable.cancel
			case None => None
		}

		// free memory
		glBindBuffer(GL_ARRAY_BUFFER, 0)
		glDeleteBuffers(vertexBufferId)

		glBindVertexArray(0)
		glDeleteVertexArrays(vertexArrayId)

		glUseProgram(0)
		glDetachShader(programId, shaderVertexId)
		glDetachShader(programId, shaderFragmentId)
		glDeleteShader(shaderVertexId)
		glDeleteShader(shaderFragmentId)
		glDeleteProgram(programId)

		textures foreach (x => glDeleteTextures(x._2))

		// shutdown OpenGL
		Display.destroy()
	}

	def redraw() {
		glClear(GL_COLOR_BUFFER_BIT)

		tiles.foreach(x => drawTile(x._2))

		checkGLError

		Display.update()
	}

	def drawTile(tile: Tile) {
		glUseProgram(programId)

		// prepare matrix
		val modelMatrix = new Matrix4f()
		modelMatrix.translate(new Vector2f(tile.x.floatValue, tile.y.floatValue))
		modelMatrix.scale(new Vector3f(tile.w.floatValue, tile.h.floatValue, 1))
		modelMatrix.rotate(tile.r.floatValue, new Vector3f(0, 0, 1))

		val mvp = buildProjectionMatrix
		Matrix4f.mul(projectionMatrix, modelMatrix, mvp)

		val matrixBuffer = BufferUtils.createFloatBuffer(16)
		mvp.store(matrixBuffer)
		matrixBuffer.flip()
		glUniformMatrix4(uniformIdMatrix, false, matrixBuffer)

		// prepare texture (can raise exception!)
		glActiveTexture(GL_TEXTURE0)
		glBindTexture(GL_TEXTURE_2D, textures(tile.id))
		glUniform1i(uniformIdTexture, 0)

		// draw
		glEnableVertexAttribArray(0)
		glBindBuffer(GL_ARRAY_BUFFER, vertexBufferId)
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0)

		glDrawArrays(GL_TRIANGLES, 0, 6)

		// clean up
		glDisableVertexAttribArray(0)
		glUseProgram(0)
	}

	def loadShader(name: String, stype: Integer): Integer = {
		val source = Source.fromURL(getClass.getResource(s"/shaders/$name.glsl"))
		val lines = source.mkString
		source.close

		val shaderID = glCreateShader(stype)
		glShaderSource(shaderID, lines)
		glCompileShader(shaderID)

		shaderID
	}

	def loadPNGTexture(name: String): Integer = {
		val in = getClass.getResourceAsStream(s"/tiles/$name.png")
		if (in == null) {
			throw new IllegalArgumentException(s"tile '$name' not found!")
		}

		val decoder = new PNGDecoder(in)

		val width = decoder.getWidth()
		val height = decoder.getHeight()

		val buf = ByteBuffer.allocateDirect(4 * width * height)
		decoder.decode(buf, width * 4, PNGDecoder.Format.RGBA)
		buf.flip()

		in.close()

		val textureId = glGenTextures()
		glBindTexture(GL_TEXTURE_2D, textureId)
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf)

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)

		checkGLError

		textureId
	}

	def buildProjectionMatrix(): Matrix4f = {
		val matrix = new Matrix4f
		val scale = 40

		matrix.setIdentity()
		matrix.m00 = 2 / dimWidh.floatValue * scale
		matrix.m11 = 2 / dimHeight.floatValue * scale

		matrix
	}

	def checkGLError {
		glGetError() match {
			case GL_NO_ERROR => None
			case i => throw new RuntimeException(s"glError: ${gluErrorString(i)}")
		}
	}
}
