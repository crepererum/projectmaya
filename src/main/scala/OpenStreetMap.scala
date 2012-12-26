import akka.actor.Actor
import akka.actor.Cancellable
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.mutable.HashSet

class OpenStreetMap extends Actor {
	case class FetchSector(x: Integer, y: Integer)

	val SECTOR_SIZE = 200
	val SECTOR_PRECISION = 8
	val Tick = "tick"
	var schedulerCancellable: Option[Cancellable] = None
	val playerActor = context.actorFor("../../World/Player")
	val fetchedSectors = HashSet.empty[(Integer, Integer)]
	val root = GeoLocation(8.4042, 49.0109)

	def receive = {
		case Tick => {
			implicit val timeout = Timeout(5.seconds)
			val future = playerActor ? AvatarCommands.GetPosition
			val pos = Await.result(future, timeout.duration).asInstanceOf[Position]
			val sectorX = (pos.x / SECTOR_SIZE).toInt
			val sectorY = (pos.y / SECTOR_SIZE).toInt

			(-1 to 1) foreach (dx => {
				(-1 to 1) foreach (dy => {
					self ! FetchSector(sectorX + dx, sectorY + dy)
				})
			})
		}
		case FetchSector(x, y) => {
			if (!fetchedSectors.contains((x, y))) {
				val geoBegin = GeoLocation.fromXY(Position(x * SECTOR_SIZE, y * SECTOR_SIZE), root)
				val geoEnd = GeoLocation.fromXY(Position((x + 1) * SECTOR_SIZE, (y + 1) * SECTOR_SIZE), root)
				println(s"http://api.openstreetmap.org/api/0.6/map?bbox=${sectorCoordString(geoBegin.latitude)},${sectorCoordString(geoBegin.longitude)},${sectorCoordString(geoEnd.latitude)},${sectorCoordString(geoEnd.longitude)}")
				fetchedSectors += ((x, y))
			}
		}
	}

	override def preStart() {
		// setup ticks
		val sys = context.system
		import sys.dispatcher
		schedulerCancellable = Some(context.system.scheduler.schedule(0.milliseconds, 100.milliseconds, self, Tick))

	}

	override def postStop() {
		// unload scheduler
		schedulerCancellable match {
			case Some(cancellable) => cancellable.cancel
			case None => None
		}
	}

	def sectorCoordString(pos: Double) = s"%.${SECTOR_PRECISION}f" format pos
}