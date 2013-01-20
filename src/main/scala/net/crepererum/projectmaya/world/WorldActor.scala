import akka.actor.Actor
import akka.actor.Props

import net.crepererum.projectmaya.Position
import net.crepererum.projectmaya.Rotation
import net.crepererum.projectmaya.TimerCommands._

import scala.concurrent.duration._

package net.crepererum.projectmaya.world {
	object WorldCommands {
		case class GlobalWorldTick(duration: FiniteDuration)
		case class CreateNewObject(props: Props, id: String)
	}

	class WorldActor extends Actor {
		val tickLength = 50.milliseconds
		val timerActor = context.actorFor("../Timer")
		val playerActor = context.actorOf(Props(new AvatarActor(Position(0, 0), new Rotation(0))), name = "Player")

		def receive = {
			case Tick => {
				context.actorSelection("*") ! new WorldCommands.GlobalWorldTick(tickLength)
			}
			case WorldCommands.CreateNewObject(props, id) => {
				context.actorOf(props, name = id)
			}
		}

		override def preStart() {
			timerActor ! ScheduleTicks(100.milliseconds, tickLength)
		}

		override def postStop() {
			timerActor ! StopTicks
		}
	}
}
