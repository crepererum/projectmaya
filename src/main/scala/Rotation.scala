class Rotation(angle: Double) {
	val a = if (angle >= 2 * math.Pi) {
		angle - (angle / (2 * math.Pi)).toInt * 2 * math.Pi
	} else if (angle < 0) {
		angle - (angle / (2 * math.Pi) - 1).toInt * 2 * math.Pi
	} else {
		angle
	}
}