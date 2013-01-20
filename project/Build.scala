import sbt._
import Keys._

object PMBuild extends Build {
	val deploy = TaskKey[Unit]("deploy")

	lazy val root = Project(id = "ProjectMaya",
		base = file("."),
		settings = Project.defaultSettings)
}
