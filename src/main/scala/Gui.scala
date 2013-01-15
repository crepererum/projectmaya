import akka.actor.Actor
import akka.actor.ActorPath
import akka.actor.Props

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
import scala.collection.immutable.List
import scala.concurrent.duration._
import scala.io.Source

class Gui extends Actor {
	val timerActor = context.actorFor("../../Timer")
	val playerActor = context.actorFor("../../World/Player")
	val textures = HashMap.empty[String, Integer]
	val commands = HashMap.empty[ActorPath, DrawCommand]
	var scheduledCommands = HashMap.empty[ActorPath, DrawCommand]
	val dimHeight = 600
	val dimWidth = 800
	var programId = 0
	var shaderFragmentId = 0
	var shaderVertexId = 0
	var uniformIdMatrix = -1
	var uniformIdTexture = -1
	var vertexArrayId = 0
	var vertexBufferId = 0
	var tickRound = 0
	var playerPosition = Position(0, 0)
	var playerRotation = new Rotation(0)
	var guiMatrix = buildGuiMatrix
	val testerActor = context.actorOf(Props(new ViewTester(guiMatrix)), name = "ViewTester")

	def receive = {
		case Tick => {
			if (Display.isCloseRequested()) {
				context.system.shutdown
			} else {
				if (tickRound == 0) {
					testerActor ! ViewTesterCommands.TestAll(commands)
				}

				redraw

				context.actorSelection("../../World/*") ! CreateYourDrawCommand
				playerActor ! AvatarCommands.GetPosition
				playerActor ! AvatarCommands.GetRotation

				tickRound = (tickRound + 1) % 20
			}
		}
		case command: DrawCommand => {
			traverseAndLoad(command.root)
			commands += (sender.path -> command)

			testerActor ! ViewTesterCommands.TestOne(sender.path, command)
		}
		case RemoveDrawCommand => {
			if (commands.contains(sender.path)) {
				commands -= sender.path
				if (scheduledCommands.contains(sender.path)) {
					scheduledCommands -= sender.path
				}
			}
			testerActor ! ViewTesterCommands.DeleteOne(sender.path)
		}
		case pos: Position => {
			playerPosition = pos
			guiMatrix = buildGuiMatrix
			testerActor ! ViewTesterCommands.ChangeGuiMatrix(guiMatrix)
		}
		case rot: Rotation => {
			playerRotation = rot
			guiMatrix = buildGuiMatrix
			testerActor ! ViewTesterCommands.ChangeGuiMatrix(guiMatrix)
		}
		case ViewTesterCommands.DeleteOneResult(path) => {
			if (scheduledCommands.contains(path)) {
				scheduledCommands -= path
			}
		}
		case ViewTesterCommands.TestAllResult(all) => {
			scheduledCommands = all.filter(x => commands.contains(x._1))
		}
		case ViewTesterCommands.TestOneResult(path, command) => {
			if (commands.contains(path)) {
				scheduledCommands += (path -> command)
			}
		}
	}

	override def preStart() {
		// OpenGL context
		val contextAttribs = new ContextAttribs(3, 0)
		contextAttribs.withForwardCompatible(true)

		// init OpenGL
		Display.setDisplayMode(new DisplayMode(dimWidth, dimHeight))
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
		glViewport(0, 0, dimWidth, dimHeight)

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
		timerActor ! ScheduleTicks(100.milliseconds, 100.milliseconds)
	}

	override def postStop() {
		// unload scheduler
		timerActor ! StopTicks

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

		scheduledCommands.map(x => x._2).toSeq.sortBy(x => x.z).foreach(x => traverseAndDraw(x.root, guiMatrix))

		checkGLError

		Display.update()
	}

	def traverseAndLoad(node: DrawNode) {
		node match {
			case Collection(children) => children foreach (child => traverseAndLoad(child))
			case Transform(child, _, _, _, _, _) => traverseAndLoad(child)
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

	def traverseAndDraw(node: DrawNode, matrix: Matrix4f) {
		node match {
			case Collection(children) => children foreach (child => traverseAndDraw(child, matrix))
			case Transform(child, x, y, r, h, w) => {
				val childMatrix = GuiUtils.calcChildMatrix(matrix, x, y, r, h, w)
				traverseAndDraw(child, childMatrix)
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

	def buildGuiMatrix(): Matrix4f = {
		val fowMatrix = new Matrix4f
		fowMatrix.translate(new Vector2f(0, (1 - 2 / MathUtils.Phi).floatValue))

		val projectionMatrix = new Matrix4f
		val scale = 50
		projectionMatrix.m00 = 2 / dimWidth.floatValue * scale
		projectionMatrix.m11 = 2 / dimHeight.floatValue * scale

		val cameraMatrix = new Matrix4f
		cameraMatrix.rotate(-playerRotation.a.floatValue, new Vector3f(0, 0, 1))
		cameraMatrix.translate(new Vector2f(-playerPosition.x.floatValue, -playerPosition.y.floatValue))

		val result = new Matrix4f
		Matrix4f.mul(fowMatrix, projectionMatrix, result)
		Matrix4f.mul(result, cameraMatrix, result)

		result
	}

	def checkGLError {
		glGetError() match {
			case GL_NO_ERROR => None
			case i => throw new RuntimeException(s"glError: ${gluErrorString(i)}")
		}
	}
}

object ViewTesterCommands {
	case class ChangeGuiMatrix(matrix: Matrix4f)
	case class DeleteOne(path: ActorPath)
	case class DeleteOneResult(path: ActorPath)
	case class TestAll(all: HashMap[ActorPath, DrawCommand])
	case class TestAllResult(result: HashMap[ActorPath, DrawCommand])
	case class TestOne(path: ActorPath, command: DrawCommand)
	case class TestOneResult(path: ActorPath, command: DrawCommand)
}

class ViewTester(var guiMatrix: Matrix4f) extends Actor {
	val testRadius = 2.0f
	val viewTestMatrix = buildViewTestMatrix

	def receive = {
		case ViewTesterCommands.ChangeGuiMatrix(matrix) => guiMatrix = matrix
		case ViewTesterCommands.DeleteOne(path) => sender ! ViewTesterCommands.DeleteOneResult(path)
		case ViewTesterCommands.TestAll(all) => {
			val iterable = all
				.map(x => (x._1, x._2.z, traverseAndTest(x._2.root, guiMatrix)))
				.filter(x => x._3.isDefined)
				.map(x => (x._1, DrawCommand(z = x._2, root = x._3.get)))
			val map = HashMap(iterable.toSeq: _*)
			sender ! ViewTesterCommands.TestAllResult(map)
		}
		case ViewTesterCommands.TestOne(path, command) => {
			val result = traverseAndTest(command.root, guiMatrix);
			if (result.isDefined) {
				sender ! ViewTesterCommands.TestOneResult(path, command.copy(root = result.get))
			} else {
				sender ! ViewTesterCommands.DeleteOneResult(path)
			}
		}
	}

	def traverseAndTest(node: DrawNode, matrix: Matrix4f): Option[DrawNode] = {
		node match {
			case Collection(children) => {
				val tmp = children.flatMap(child => traverseAndTest(child, matrix))
				tmp.size match {
					case 0 => None
					case _ => Some(Collection(tmp))
				}
			}
			case Transform(child, x, y, r, h, w) => {
				val childMatrix = GuiUtils.calcChildMatrix(matrix, x, y, r, h, w)

				traverseAndTest(child, childMatrix) match {
					case Some(tmp) => Some(Transform(tmp, x, y, r, h, w))
					case None => None
				}
			}
			case Tile(id) => {
				val result = new Matrix4f
				Matrix4f.mul(matrix, viewTestMatrix, result)

				val xValues = List(result.m00, result.m10, result.m20, result.m30)
				val yValues = List(result.m01, result.m11, result.m21, result.m31)
				val qDistances = (xValues zip yValues) map (c => c._1 * c._1 + c._2 * c._2)
				val viewables = qDistances filter (_ <= testRadius * testRadius)

				if (viewables.size > 0) {
					Some(Tile(id))
				} else {
					None
				}
			}
		}
	}

	def buildViewTestMatrix(): Matrix4f = {
		val vectors = new Matrix4f

		vectors.m00 = -0.5f
		vectors.m01 = -0.5f
		vectors.m02 = 0
		vectors.m03 = 1

		vectors.m10 = -0.5f
		vectors.m11 = 0.5f
		vectors.m12 = 0
		vectors.m13 = 1

		vectors.m20 = 0.5f
		vectors.m21 = -0.5f
		vectors.m22 = 0
		vectors.m23 = 1

		vectors.m30 = 0.5f
		vectors.m31 = 0.5f
		vectors.m32 = 0
		vectors.m33 = 1

		vectors
	}
}

object GuiUtils {
	def calcChildMatrix(current: Matrix4f, x: Double, y: Double, r: Double, h: Double, w: Double): Matrix4f = {
		val nodeMatrix = new Matrix4f()
		nodeMatrix.translate(new Vector2f(x.floatValue, y.floatValue))
		nodeMatrix.rotate(r.floatValue, new Vector3f(0, 0, 1))
		nodeMatrix.scale(new Vector3f(w.floatValue, h.floatValue, 1))

		val childMatrix = new Matrix4f()
		Matrix4f.mul(current, nodeMatrix, childMatrix)

		childMatrix
	}
}
