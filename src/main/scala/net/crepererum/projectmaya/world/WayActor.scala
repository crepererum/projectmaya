import akka.actor.Actor

import net.crepererum.projectmaya.DrawCommands._
import net.crepererum.projectmaya.MathUtils
import net.crepererum.projectmaya.Position

import scala.collection.mutable.HashMap

package net.crepererum.projectmaya.world {
	object WayCommands {
		case class Extend(partsRaw: Map[Position, Position])
	}

	class WayActor(partsRaw: Map[Position, Position]) extends Actor {
		val parts = HashMap(partsRaw.toSeq: _*)
		val EPSILON = 0.01
		val partSize = 0.5
		val pointSize = 1
		val width = 0.8
		var drawCommandCached = false
		updateBounds

		def receive = {
			case CreateYourDrawCommand => {
				if (!drawCommandCached) {
					val partsCommands = parts.map(p => {
						val dx = p._2.x - p._1.x
						val dy = p._2.y - p._1.y

						val len = math.sqrt(dx * dx + dy * dy)
						val n = math.ceil(len / partSize).toInt

						val sx = dx / n
						val sy = dy / n

						val r = MathUtils.restoreAngle(sx, sy, EPSILON, true)

						(0 to n).toSeq.map(i => {
							val x = p._1.x + i * sx
							val y = p._1.y + i * sy
							Transform(x = x, y = y, h = partSize, w = width, r = r, child = Tile("way_part"))
						})
					}).flatten.toSeq

					val pointCommands = (parts.map(p => p._1) ++ parts.map(p => p._2).filter(p => !parts.contains(p)))
						.map(point => Transform(x = point.x, y = point.y, h = pointSize, w = pointSize, child = Tile("way_point")))
						.toSeq

					sender ! DrawCommand(z = -1, root = Collection(children = List(Collection(partsCommands), Collection(pointCommands))))
					drawCommandCached = true
				}
			}
			case UncacheYourDrawCommand => {
				drawCommandCached = false
			}
			case WayCommands.Extend(partsRaw) => {
				parts ++= partsRaw
				drawCommandCached = false
				updateBounds
			}
		}

		def updateBounds = {
			val points = parts flatMap (p => Seq(p._1, p._2))
			val xValues = points map (p => p.x)
			val yValues = points map (p => p.y)

			context.parent ! WorldCommands.SetBounds(
				Position(xValues.min - pointSize, yValues.min - pointSize),
				Position(xValues.max + pointSize, yValues.max + pointSize))
		}
	}
}
