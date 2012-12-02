import akka.actor.ActorSystem
import akka.actor.Props
import scala.concurrent.duration._

object ProjectMaya extends App {
	val sys = ActorSystem("ProjectMaya")
	val world = sys.actorOf(Props[World], name = "World")
	val gui = sys.actorOf(Props[Gui].withDispatcher("akka.actor.threadbound-dispatcher"), name = "Gui")
	val input = sys.actorOf(Props[Input].withDispatcher("akka.actor.threadbound-dispatcher"), name = "Input")
}
