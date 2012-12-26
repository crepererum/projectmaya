import akka.actor.Actor
import akka.actor.Cancellable
import akka.actor.Props

import scala.concurrent.duration._

case class GlobalWorldTick(duration: FiniteDuration)

class World extends Actor {
	val Tick = "tick"
	val tickLength = 50.milliseconds
	var schedulerCancellable: Option[Cancellable] = None
	val playerActor = context.actorOf(Props(new Avatar(Position(0, 0), 0)), name = "Player")

	def receive = {
		case Tick => {
			context.actorSelection("*") ! new GlobalWorldTick(tickLength)
		}
	}

	override def preStart() {
		// setup ticks
		val sys = context.system
		import sys.dispatcher
		schedulerCancellable = Some(context.system.scheduler.schedule(100.milliseconds, tickLength, self, Tick))

	}

	override def postStop() {
		// unload scheduler
		schedulerCancellable match {
			case Some(cancellable) => cancellable.cancel
			case None => None
		}
	}
}