import akka.actor.ActorSystem
import akka.actor.Props

object ProjectMaya extends App {
	val sys = ActorSystem("ProjectMaya")
	val world = sys.actorOf(Props[World], name = "World")
	val gateways = sys.actorOf(Props[Gateways], name = "Gateways")
	val client = sys.actorOf(Props[Client], name = "Client")
}
