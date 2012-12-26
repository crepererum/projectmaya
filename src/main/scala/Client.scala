import akka.actor.Actor
import akka.actor.Props

class Client extends Actor {
	val gui = context.actorOf(Props[Gui].withDispatcher("akka.actor.threadbound-dispatcher"), name = "Gui")
	val input = context.actorOf(Props[Input].withDispatcher("akka.actor.threadbound-dispatcher"), name = "Input")

	def receive = {
		case _ => None
	}
}