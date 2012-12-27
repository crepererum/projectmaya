import akka.actor.Actor
import akka.actor.ActorPath
import akka.actor.Cancellable

import de.matthiasmann.twl.utils.PNGDecoder

import java.nio.ByteBuffer

import org.lwjgl.BufferUtils
import org.lwjgl.LWJGLException
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

class Gui extends Actor {
	val Tick = "tick"
	val textures = HashMap.empty[String, Integer]
	val commands = HashMap.empty[ActorPath, DrawCommand]
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
				context.actorSelection("../../World/*") ! CreateYourDrawCommand
			}
		}
		case command: DrawCommand => {
			searchForTiles(command.root)
			commands += (sender.path -> command)
		}
		case RemoveDrawCommand => {
			commands -= sender.path
		}
	}

	override def preStart() {
		// OpenGL context
		val contextAttribs = new ContextAttribs(3, 0)
		contextAttribs.withForwardCompatible(true)

		// init OpenGL
		Display.setDisplayMode(new DisplayMode(dimWidh, dimHeight))
		Display.setTitle("Project Maya")

		// try mulisample format
		try {
			Display.create(new PixelFormat withSamples (8), contextAttribs)
		} catch {
			case e: LWJGLException => Display.create(new PixelFormat, contextAttribs)
		}

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

	def searchForTiles(node: DrawNode) {
		node match {
			case Collection(chields) => chields foreach (chield => searchForTiles(chield))
			case Transform(chield, _, _, _, _, _) => searchForTiles(chield)
			case Tile(id) => {
				try {
					if (!textures.contains(id)) {
						textures += (id -> loadPNGTexture(id))
					}
				} catch {
					case e: IllegalArgumentException => {
						textures += (id -> loadPNGTexture("dummy"))
					}
				}
			}
		}
	}

	def redraw() {
		glClear(GL_COLOR_BUFFER_BIT)

		commands.map(x => x._2).toSeq.sortBy(x => x.z).foreach(x => executeNode(x.root, projectionMatrix))

		checkGLError

		Display.update()
	}

	def executeNode(node: DrawNode, matrix: Matrix4f) {
		node match {
			case Collection(chields) => chields foreach (chield => executeNode(chield, matrix))
			case Transform(chield, x, y, r, h, w) => {
				val nodeMatrix = new Matrix4f()
				nodeMatrix.translate(new Vector2f(x.floatValue, y.floatValue))
				nodeMatrix.scale(new Vector3f(w.floatValue, h.floatValue, 1))
				nodeMatrix.rotate(r.floatValue, new Vector3f(0, 0, 1))

				val chieldMatrix = new Matrix4f()
				Matrix4f.mul(matrix, nodeMatrix, chieldMatrix)

				executeNode(chield, chieldMatrix)
			}
			case Tile(id) => {
				glUseProgram(programId)

				// prepare matrix
				val matrixBuffer = BufferUtils.createFloatBuffer(16)
				matrix.store(matrixBuffer)
				matrixBuffer.flip()
				glUniformMatrix4(uniformIdMatrix, false, matrixBuffer)

				// prepare texture (can raise exception!)
				glActiveTexture(GL_TEXTURE0)
				glBindTexture(GL_TEXTURE_2D, textures(id))
				glUniform1i(uniformIdTexture, 0)

				// draw
				glEnableVertexAttribArray(0)
				glBindBuffer(GL_ARRAY_BUFFER, vertexBufferId)
				glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0)

				glDrawArrays(GL_TRIANGLES, 0, 6)

				// clean up
				glDisableVertexAttribArray(0)
				glUseProgram(0)
				checkGLError
			}
		}
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
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
		glGenerateMipmap(GL_TEXTURE_2D)

		checkGLError

		textureId
	}

	def buildProjectionMatrix(): Matrix4f = {
		val matrix = new Matrix4f
		val scale = 50

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
