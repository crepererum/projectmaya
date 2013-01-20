import akka.actor.Actor

import net.crepererum.projectmaya.TimerCommands._
import net.crepererum.projectmaya.world.AvatarCommands

import org.lwjgl.input.Keyboard

import scala.concurrent.duration._

package net.crepererum.projectmaya.client {
	class InputActor extends Actor {
		val playerActor = context.actorFor("../../World/Player")
		val timerActor = context.actorFor("../../Timer")

		def receive = {
			case Tick => {
				while (Keyboard.next()) {
					(Keyboard.getEventKey(), Keyboard.getEventKeyState()) match {
						case (Keyboard.KEY_DOWN, true) => playerActor ! AvatarCommands.MoveBackwardBegin
						case (Keyboard.KEY_DOWN, false) => playerActor ! AvatarCommands.MoveBackwardEnd
						case (Keyboard.KEY_ESCAPE, true) => context.system.shutdown
						case (Keyboard.KEY_LEFT, true) => playerActor ! AvatarCommands.RotateLeftBegin
						case (Keyboard.KEY_LEFT, false) => playerActor ! AvatarCommands.RotateLeftEnd
						case (Keyboard.KEY_RIGHT, true) => playerActor ! AvatarCommands.RotateRightBegin
						case (Keyboard.KEY_RIGHT, false) => playerActor ! AvatarCommands.RotateRightEnd
						case (Keyboard.KEY_UP, true) => playerActor ! AvatarCommands.MoveForwardBegin
						case (Keyboard.KEY_UP, false) => playerActor ! AvatarCommands.MoveForwardEnd
						case _ => None
					}
				}
			}
		}

		override def preStart() {
			timerActor ! ScheduleTicks(100.milliseconds, 50.milliseconds)
		}

		override def postStop() {
			timerActor ! StopTicks
		}
	}
}
