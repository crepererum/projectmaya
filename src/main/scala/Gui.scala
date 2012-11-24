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

import scala.collection.mutable.HashMap
import scala.concurrent.duration._

class Tile(x: Double, y: Double, z: Integer, r: Double, id: String)

class Gui extends Actor {
	val Tick = "tick"
	val tiles = HashMap.empty[ActorPath, Tile]
	var schedulerCancellable : Option[Cancellable] = None
	var vaoId = 0
	var vboId = 0
	
	def receive = {
		case Tick => {
			if (Display.isCloseRequested()) {
				context.system.shutdown
			} else {
				redraw
			}
		}
		case tile: Tile => {
			tiles += (sender.path -> tile)
		}
	}
	
	override def preStart() {
		// init OpenGL
		Display.setDisplayMode(new DisplayMode(800, 600))
		Display.setTitle("Project Maya")
		Display.create()
		
		// set viewport
		glViewport(0, 0, 800, 600)
		
		// load data
		val vertices = Array[Float](
				-.5f,  .5f, 0f,
				-.5f, -.5f, 0f,
				 .5f, -.5f, 0f,
				 
				 .5f, -.5f, 0f,
				 .5f,  .5f, 0f,
				-.5f,  .5f, 0f)
		
		val verticesBuffer = BufferUtils.createFloatBuffer(vertices.length)
		verticesBuffer.put(vertices)
		verticesBuffer.flip()
		
		vaoId = glGenVertexArrays()
		glBindVertexArray(vaoId)
		
		vboId = glGenBuffers()
		glBindBuffer(GL_ARRAY_BUFFER, vboId)
		glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW)
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0)
		
		glBindBuffer(GL_ARRAY_BUFFER, 0)
		glBindVertexArray(0)
		
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
		glDisableVertexAttribArray(0)
		
		glBindBuffer(GL_ARRAY_BUFFER, 0)
		glDeleteBuffers(vboId)
		
		glBindVertexArray(0)
		glDeleteVertexArrays(vaoId)
		
		// shutdown OpenGL
		Display.destroy()
	}
	
	def redraw() {
		glClear(GL_COLOR_BUFFER_BIT)
		
		glBindVertexArray(vaoId)
		glEnableVertexAttribArray(0)
		
		glDrawArrays(GL_TRIANGLES, 0, 6)
		
		glDisableVertexAttribArray(0)
		glBindVertexArray(0)
		
		Display.update()
	}
}
