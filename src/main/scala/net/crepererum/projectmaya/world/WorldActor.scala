import akka.actor.Actor
import akka.actor.ActorPath
import akka.actor.Props

import com.infomatiq.jsi.Rectangle
import com.infomatiq.jsi.SpatialIndex
import com.infomatiq.jsi.rtree.RTree

import gnu.trove.TIntProcedure

import net.crepererum.projectmaya.Position
import net.crepererum.projectmaya.Rotation
import net.crepererum.projectmaya.TimerCommands._

import scala.collection.mutable.HashMap
import scala.concurrent.duration._

package net.crepererum.projectmaya.world {
	object WorldCommands {
		case class GlobalWorldTick(duration: FiniteDuration)
		case class CreateNewObject(props: Props, id: String)
		case class SetBounds(a: Position, b: Position)
		case class RectBroadcast(a: Position, b: Position, payload: Any)
	}

	class WorldActor extends Actor {
		val tickLength = 50.milliseconds
		val timerActor = context.actorFor("../Timer")
		val playerActor = context.actorOf(Props(new AvatarActor(Position(0, 0), new Rotation(0))), name = "Player")
		val index: SpatialIndex = new RTree; index.init(null)
		val indexDict = HashMap.empty[ActorPath, (Rectangle, Integer)]
		val indexReverseDict = HashMap.empty[Integer, ActorPath]
		var indexCounter = 1

		def receive = {
			case Tick => {
				context.actorSelection("*") ! new WorldCommands.GlobalWorldTick(tickLength)
			}
			case WorldCommands.CreateNewObject(props, id) => {
				context.actorOf(props, name = id)
			}
			case WorldCommands.SetBounds(a, b) => {
				val id: Integer = if (indexDict.contains(sender.path)) {
					val tmp = indexDict(sender.path)
					index.delete(tmp._1, tmp._2)
					indexDict -= sender.path
					tmp._2
				} else {
					val tmp: Integer = indexCounter
					indexCounter += 1
					indexReverseDict += (tmp -> sender.path)
					tmp
				}

				val rect = new Rectangle(a.x.floatValue, a.y.floatValue, b.x.floatValue, b.y.floatValue)
				index.add(rect, id)
				indexDict += (sender.path -> (rect, id))
			}
			case WorldCommands.RectBroadcast(a, b, payload) => {
				val rect = new Rectangle(a.x.floatValue, a.y.floatValue, b.x.floatValue, b.y.floatValue)

				index.intersects(rect, new TIntProcedure {
					override def execute(i: Int): Boolean = {
						context.actorFor(indexReverseDict(i)) forward payload
						true
					}
				})
			}
		}

		override def preStart() {
			timerActor ! ScheduleTicks(100.milliseconds, tickLength)
		}

		override def postStop() {
			timerActor ! StopTicks
		}
	}
}
