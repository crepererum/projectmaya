object MathUtils {
	val Phi = (1.0 + math.sqrt(5)) / 2.0

	def restoreAngle(x: Double, y: Double, epsilon: Double, inverse: Boolean = true): Double = {
		val x2 = if (inverse) -x else x

		if (math.abs(y) < epsilon) (math.Pi / 2.0 * math.signum(x2))
		else if (y > 0) math.atan(x2 / y)
		else if (math.abs(x2) < epsilon) math.Pi
		else if (x2 > 0) (math.Pi / 2 + math.atan(-y / x2))
		else (-math.Pi / 2 - math.atan(y / x2))
	}
}