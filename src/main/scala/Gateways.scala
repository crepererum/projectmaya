import akka.actor.Actor
import akka.actor.Props

class Gateways extends Actor {
	val osm = context.actorOf(Props[OpenStreetMap], name = "OpenStreetMap")

	def receive = {
		case _ => None
	}
}