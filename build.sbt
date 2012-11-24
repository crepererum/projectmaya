name := "ProjectMaya"

version := "Day0"

scalaVersion := "2.10.0-RC2"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= List(
	"com.typesafe.akka" %% "akka-actor" % "2.1.0-RC2" cross CrossVersion.full,
	"org.lwjgl.lwjgl" % "lwjgl" % "2.8.5"
)

LWJGLPlugin.lwjglSettings ++ Seq(
	LWJGLPlugin.lwjgl.version := "2.8.5"
)

scalacOptions ++= Seq(
	"-deprecation",
	"-feature",
	"-optimize",
	"-unchecked"
)
