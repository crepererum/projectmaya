name := "ProjectMaya"

version := "Day0"

scalaVersion := "2.10.0-RC5"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= List(
	"com.typesafe.akka" %% "akka-actor" % "2.1.0",
	"org.lwjgl.lwjgl" % "lwjgl" % "2.8.5"
)

LWJGLPlugin.lwjglSettings ++ Seq(
	LWJGLPlugin.lwjgl.version := "2.8.5"
)

javacOptions ++= Seq(
	"-source",
	"1.7",
	"-target",
	"1.7"
)

scalacOptions ++= Seq(
	"-deprecation",
	"-feature",
	"-optimize",
	"-target:jvm-1.7",
	"-unchecked"
)
