import akka.actor.Actor
import akka.actor.ActorPath
import akka.actor.Cancellable

import scala.collection.mutable.HashMap
import scala.concurrent.duration.FiniteDuration

case class ScheduleTicks(initialDelay: FiniteDuration, interval: FiniteDuration)
case object StopTicks
case object Tick

class Timer extends Actor {
	val scheduledTasks = HashMap.empty[ActorPath, Cancellable]

	def receive = {
		case ScheduleTicks(initialDelay, interval) => {
			stopTicks(sender.path)

			val sys = context.system
			import sys.dispatcher
			val cancellable = context.system.scheduler.schedule(initialDelay, interval, sender, Tick)

			scheduledTasks += (sender.path -> cancellable)
		}
		case StopTicks => stopTicks(sender.path)
	}

	override def postStop() {
		scheduledTasks foreach (x => x._2.cancel)
	}

	def stopTicks(path: ActorPath) {
		if (scheduledTasks.contains(path)) {
			scheduledTasks(path).cancel
			scheduledTasks -= path
		}
	}
}