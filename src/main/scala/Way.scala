import akka.actor.Actor

class Way(points: Seq[Position]) extends Actor {
	val EPSILON = 0.01
	val partSize = 0.5
	val pointSize = 1
	val width = 0.8
	var drawCommandCreated = false

	def receive = {
		case CreateYourDrawCommand => {
			if (!drawCommandCreated) {
				val partsCommands = (points zip points.tail).map(p => {
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
				}).flatten

				val pointCommands = points.map(point => Transform(x = point.x, y = point.y, h = pointSize, w = pointSize, child = Tile("way_point")))
				sender ! DrawCommand(z = -1, root = Collection(children = List(Collection(partsCommands), Collection(pointCommands))))
				drawCommandCreated = true
			}
		}
	}
}