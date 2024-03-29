import net.crepererum.projectmaya.MathUtils
import net.crepererum.projectmaya.Position
import net.crepererum.projectmaya.world.AvatarCommands

package net.crepererum.projectmaya.gateways {
	object GeoLocation {
		val EPSILON = 0.000001
		val RADIUS_EARTH = 6371 * 1000

		def fromXY(pos: Position, root: GeoLocation): GeoLocation = {
			val xNorm = pos.x / RADIUS_EARTH
			val yNorm = pos.y / RADIUS_EARTH
			val zeta = math.sqrt(xNorm * xNorm + yNorm * yNorm)
			val alpha = MathUtils.restoreAngle(xNorm, yNorm, EPSILON, false)
			val radLatitude = math.asin(math.cos(alpha) * math.cos(root.radLatitude) * math.sin(zeta) + math.sin(root.radLatitude) * math.cos(zeta))
			val radLongitude =
				if ((math.abs(alpha) < EPSILON) || ((math.abs(math.Pi - alpha) < EPSILON)) || ((math.abs(-math.Pi - alpha) < EPSILON))) root.radLongitude
				else math.acos((math.cos(zeta) - math.sin(root.radLatitude) * math.sin(radLatitude)) / (math.cos(root.radLatitude) * math.cos(radLatitude))) * math.signum(xNorm) + root.radLongitude
			val latitude = radLatitude / (math.Pi * 2) * 360
			val longitude = radLongitude / (math.Pi * 2) * 360

			GeoLocation(latitude, longitude)
		}
	}

	case class GeoLocation(latitude: Double, longitude: Double) {
		def toXY(root: GeoLocation): Position = {
			val zeta = math.acos(math.sin(root.radLatitude) * math.sin(radLatitude) + math.cos(root.radLatitude) * math.cos(radLatitude) * math.cos(radLongitude - root.radLongitude))
			val alpha =
				if ((math.abs(radLongitude - root.radLongitude) < GeoLocation.EPSILON) && (math.abs(radLatitude - root.radLatitude) < GeoLocation.EPSILON)) 0.0
				else if ((math.abs(radLongitude - root.radLongitude) < GeoLocation.EPSILON) && (math.signum(radLatitude - root.radLatitude) > 0)) 0.0
				else if ((math.abs(radLongitude - root.radLongitude) < GeoLocation.EPSILON) && (math.signum(radLatitude - root.radLatitude) < 0)) math.Pi
				else math.acos((math.sin(radLatitude) - math.sin(root.radLatitude) * math.cos(zeta)) / (math.cos(root.radLatitude) * math.sin(zeta))) * math.signum(radLongitude - root.radLongitude)
			val x = math.sin(alpha) * zeta * GeoLocation.RADIUS_EARTH
			val y = math.cos(alpha) * zeta * GeoLocation.RADIUS_EARTH

			Position(x, y)
		}

		def radLatitude = latitude / 360.0 * math.Pi * 2
		def radLongitude = longitude / 360.0 * math.Pi * 2
	}
}
