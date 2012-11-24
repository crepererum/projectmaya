import akka.actor.Actor

import scala.concurrent.duration.FiniteDuration

object AvatarCommands {
	case object MoveBackwardBegin
	case object MoveBackwardEnd
	case object MoveForwardBegin
	case object MoveForwardEnd
	case object RotateLeftBegin
	case object RotateLeftEnd
	case object RotateRightBegin
	case object RotateRightEnd
}

class Avatar(var posX: Double, var posY: Double, var rotation: Double) extends Actor {
	val rotatationSpeed = 0.2
	val walkSpeed = 1.0
	var rotateLeft = false
	var rotateRight = false
	var walkForward = false
	var walkBackward = false
	
	def receive = {
		case GlobalWorldTick(duration) => step(duration)
		case AvatarCommands.MoveBackwardBegin => walkBackward = true
		case AvatarCommands.MoveBackwardEnd => walkBackward = false
		case AvatarCommands.MoveForwardBegin => walkForward = true
		case AvatarCommands.MoveForwardEnd => walkForward = false
		case AvatarCommands.RotateLeftBegin => rotateLeft = true
		case AvatarCommands.RotateLeftEnd => rotateLeft = false
		case AvatarCommands.RotateRightBegin => rotateRight = true
		case AvatarCommands.RotateRightEnd => rotateRight = false
	}
	
	def step(duration: FiniteDuration) {
		val seconds = duration.toMillis.toDouble / 1000
		
		if (rotateLeft) {
			rotation -= seconds * 2 * math.Pi * rotatationSpeed
		}
		if (rotateRight) {
			rotation += seconds * 2 * math.Pi * rotatationSpeed
		}
		if (rotation >= 2 * math.Pi) {
			rotation -= (rotation / (2 * math.Pi)).toInt * 2 * math.Pi
		}
		if (rotation < 0) {
			rotation -= (rotation / (2 * math.Pi) - 1).toInt * 2 * math.Pi
		}
		
		if (walkForward) {
			posX += seconds * walkSpeed * Math.cos(rotation)
			posY += seconds * walkSpeed * Math.sin(rotation)
		}
		if (walkBackward) {
			posX -= seconds * walkSpeed * Math.cos(rotation)
			posY -= seconds * walkSpeed * Math.sin(rotation)
		}
		
		println(s"x=${posX} y=${posY} r=${rotation}")
	}
}