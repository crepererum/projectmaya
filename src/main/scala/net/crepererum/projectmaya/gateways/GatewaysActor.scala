import akka.actor.Actor
import akka.actor.Props

package net.crepererum.projectmaya.gateways {
	class GatewaysActor extends Actor {
		val osm = context.actorOf(Props[OpenStreetMapActor], name = "OpenStreetMap")

		def receive = {
			case _ => None
		}
	}
}
