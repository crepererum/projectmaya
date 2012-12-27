import akka.actor.Actor
import akka.actor.Props

import scala.concurrent.duration._

case class GlobalWorldTick(duration: FiniteDuration)
case class CreateNewObject(props: Props, id: String)

class World extends Actor {
	val tickLength = 50.milliseconds
	val timerActor = context.actorFor("../Timer")
	val playerActor = context.actorOf(Props(new Avatar(Position(0, 0), 0)), name = "Player")

	def receive = {
		case Tick => {
			context.actorSelection("*") ! new GlobalWorldTick(tickLength)
		}
		case CreateNewObject(props, id) => {
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