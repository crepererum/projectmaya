import akka.actor.Actor
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout

import net.crepererum.projectmaya.Position
import net.crepererum.projectmaya.TimerCommands._
import net.crepererum.projectmaya.world.AvatarCommands
import net.crepererum.projectmaya.world.WayActor
import net.crepererum.projectmaya.world.WayCommands
import net.crepererum.projectmaya.world.WorldCommands

import java.net.URL
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.xml.Elem
import scala.xml.XML

package net.crepererum.projectmaya.gateways {
	class OpenStreetMapActor extends Actor {
		case class FetchSector(x: Integer, y: Integer)

		val SECTOR_SIZE = 100
		val SECTOR_PRECISION = 8
		val worldActor = context.actorFor("../../World")
		val playerActor = context.actorFor("../../World/Player")
		val timerActor = context.actorFor("../../Timer")
		val fetchedSectors = HashSet.empty[(Integer, Integer)]
		val createdWays = HashSet.empty[Integer]
		val root = GeoLocation(latitude = 49.0109, longitude = 8.4042)
		val nodes = HashMap.empty[Integer, Position]

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
					val urlString = s"http://api.openstreetmap.org/api/0.6/map?bbox=${sectorCoordString(geoBegin.longitude)},${sectorCoordString(geoBegin.latitude)},${sectorCoordString(geoEnd.longitude)},${sectorCoordString(geoEnd.latitude)}"
					val xml = XML.load(new URL(urlString))

					parseXML(xml)

					fetchedSectors += ((x, y))
				}
			}
		}

		override def preStart() {
			timerActor ! ScheduleTicks(0.milliseconds, 100.milliseconds)
		}

		override def postStop() {
			timerActor ! StopTicks
		}

		def parseXML(xml: Elem) {
			scala.xml.Utility.trim(xml) match {
				case <osm>{ rootElements @ _* }</osm> => rootElements.foreach(rootElement => rootElement match {
					case <bounds>{ boundsParams @ _* }</bounds> => Unit // ignore
					case <node>{ nodeParams @ _* }</node> => {
						val id: Integer = (rootElement \ "@id").text.toInt
						val lat = (rootElement \ "@lat").text.toDouble
						val lon = (rootElement \ "@lon").text.toDouble

						if (!nodes.contains(id)) {
							val geoLoc = new GeoLocation(latitude = lat, longitude = lon)
							nodes += (id -> geoLoc.toXY(root))
						}
					}
					case <way>{ wayParams @ _* }</way> => {
						val points = wayParams
							.filter(wayParam => wayParam.label == "nd")
							.map(nd => (nd \ "@ref").text.toInt.asInstanceOf[Integer])
							.map(refId => nodes(refId))
						val pointMap = Map((points zip points.tail): _*)
						val number = (rootElement \ "@id").text.toInt
						val id = s"way_${number}"

						if (createdWays.contains(number)) {
							context.actorFor(s"../../World/$id") ! WayCommands.Extend(pointMap)
						} else {
							worldActor ! WorldCommands.CreateNewObject(Props(new WayActor(pointMap)), id)
							createdWays += number
						}
					}
					case <relation>{ relationParams @ _* }</relation> => Unit // ignore
					case _ => println(s"Ignore element ${rootElement.label}")
				})
				case _ => println("Malformed XML!")
			}
		}

		def sectorCoordString(pos: Double): String = {
			val symbols = new DecimalFormatSymbols
			symbols.setDecimalSeparator('.')

			val format = new DecimalFormat
			format.setMaximumFractionDigits(SECTOR_PRECISION)
			format.setDecimalFormatSymbols(symbols)

			format.format(pos)
		}
	}
}
