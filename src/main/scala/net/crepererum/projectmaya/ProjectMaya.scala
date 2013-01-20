import akka.actor.ActorSystem
import akka.actor.Props

import net.crepererum.projectmaya.client.ClientActor
import net.crepererum.projectmaya.gateways.GatewaysActor
import net.crepererum.projectmaya.world.WorldActor

package net.crepererum.projectmaya {
	object ProjectMaya extends App {
		val sys = ActorSystem("ProjectMaya")
		val shell = sys.actorOf(Props[ShellActor], name = "Shell")
		val timer = sys.actorOf(Props[TimerActor], name = "Timer")
		val world = sys.actorOf(Props[WorldActor], name = "World")
		val gateways = sys.actorOf(Props[GatewaysActor], name = "Gateways")
		val client = sys.actorOf(Props[ClientActor], name = "Client")
	}
}
