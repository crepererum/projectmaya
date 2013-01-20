import akka.actor.Actor
import akka.actor.Props

package net.crepererum.projectmaya.client {
	class ClientActor extends Actor {
		val gui = context.actorOf(Props[GuiActor].withDispatcher("akka.actor.threadbound-dispatcher"), name = "Gui")
		val input = context.actorOf(Props[InputActor].withDispatcher("akka.actor.threadbound-dispatcher"), name = "Input")

		def receive = {
			case _ => None
		}
	}
}
