import akka.actor.Actor
import akka.actor.ActorPath
import akka.actor.Cancellable

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.DisplayMode
import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL30._
import org.lwjgl.util.vector.Matrix4f
import org.lwjgl.util.vector.Vector2f
import org.lwjgl.util.vector.Vector3f

import scala.collection.mutable.HashMap
import scala.concurrent.duration._
import scala.io.Source

case object showYourTile

case class Tile(x: Double, y: Double, z: Integer, r: Double, h: Double, w: Double, id: String)

class Gui extends Actor {
	val Tick = "tick"
	val tiles = HashMap.empty[ActorPath, Tile]
	val dimHeight = 600
	val dimWidh = 800
	val projectionMatrix = buildProjectionMatrix
	var schedulerCancellable: Option[Cancellable] = None
	var pId = 0
	var shaderFragmentId = 0
	var shaderVertexId = 0
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
			tiles += (sender.path -> tile)
		}
	}

	override def preStart() {
		// init OpenGL
		Display.setDisplayMode(new DisplayMode(dimWidh, dimHeight))
		Display.setTitle("Project Maya")
		Display.create()

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
		pId = glCreateProgram()
		glAttachShader(pId, shaderVertexId)
		glAttachShader(pId, shaderFragmentId)
		glLinkProgram(pId)
		glValidateProgram(pId)

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
		glDetachShader(pId, shaderVertexId)
		glDetachShader(pId, shaderFragmentId)
		glDeleteShader(shaderVertexId)
		glDeleteShader(shaderFragmentId)
		glDeleteProgram(pId)

		// shutdown OpenGL
		Display.destroy()
	}

	def redraw() {
		glClear(GL_COLOR_BUFFER_BIT)

		tiles.foreach(x => drawTile(x._2))

		Display.update()
	}

	def drawTile(tile: Tile) {
		glUseProgram(pId)

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
		glUniformMatrix4(0, false, matrixBuffer)

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

	def buildProjectionMatrix(): Matrix4f = {
		val matrix = new Matrix4f
		val scale = 40

		matrix.setIdentity()
		matrix.m00 = 2 / dimWidh.floatValue * scale
		matrix.m11 = 2 / dimHeight.floatValue * scale

		matrix
	}
}
