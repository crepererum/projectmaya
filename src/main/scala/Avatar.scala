import akka.actor.Actor

import scala.concurrent.duration.FiniteDuration

object AvatarCommands {
	case object GetPosition
	case object GetRotation
	case object MoveBackwardBegin
	case object MoveBackwardEnd
	case object MoveForwardBegin
	case object MoveForwardEnd
	case object RotateLeftBegin
	case object RotateLeftEnd
	case object RotateRightBegin
	case object RotateRightEnd
}

class Avatar(var pos: Position, var rotation: Rotation) extends Actor {
	val rotatationSpeed = 0.2
	val walkSpeed = 3.0
	var rotateLeft = false
	var rotateRight = false
	var walkForward = false
	var walkBackward = false

	def receive = {
		case GlobalWorldTick(duration) => step(duration)
		case AvatarCommands.GetPosition => sender ! pos
		case AvatarCommands.GetRotation => sender ! rotation
		case AvatarCommands.MoveBackwardBegin => walkBackward = true
		case AvatarCommands.MoveBackwardEnd => walkBackward = false
		case AvatarCommands.MoveForwardBegin => walkForward = true
		case AvatarCommands.MoveForwardEnd => walkForward = false
		case AvatarCommands.RotateLeftBegin => rotateLeft = true
		case AvatarCommands.RotateLeftEnd => rotateLeft = false
		case AvatarCommands.RotateRightBegin => rotateRight = true
		case AvatarCommands.RotateRightEnd => rotateRight = false
		case showYourTile => sender ! new DrawCommand(
			z = 0,
			root = new Transform(
				x = pos.x,
				y = pos.y,
				h = 0.4,
				w = 0.35,
				r = rotation.a,
				child = new Tile(id = "foo")))
	}

	def step(duration: FiniteDuration) {
		val seconds = duration.toMillis.toDouble / 1000

		if (rotateLeft) {
			rotation = new Rotation(rotation.a + seconds * 2 * math.Pi * rotatationSpeed)
		}
		if (rotateRight) {
			rotation = new Rotation(rotation.a - seconds * 2 * math.Pi * rotatationSpeed)
		}

		if (walkForward) {
			pos = Position(
				x = pos.x - seconds * walkSpeed * Math.sin(rotation.a),
				y = pos.y + seconds * walkSpeed * Math.cos(rotation.a))
		}
		if (walkBackward) {
			pos = Position(
				x = pos.x + seconds * walkSpeed * Math.sin(rotation.a),
				y = pos.y - seconds * walkSpeed * Math.cos(rotation.a))
		}

		println(s"x=${pos.x} y=${pos.y} r=${rotation.a}")
	}
}