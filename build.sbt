import AssemblyKeys._

assemblySettings

name := "ProjectMaya"

version := "Day0"

scalaVersion := "2.10.0"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= List(
	"com.typesafe.akka" %% "akka-actor" % "2.1.0",
	"org.lwjgl.lwjgl" % "lwjgl" % "2.8.5"
)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _ )

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

jarName in assembly := "ProjectMaya.jar"

deploy <<= (lwjgl.copyDir) map {dir =>
	IO.createDirectory(new File("deploy"))
	IO.copyFile(new File("target/ProjectMaya.jar"), new File("deploy/ProjectMaya.jar"))
	IO.listFiles(dir) foreach {subdir =>
		IO.listFiles(subdir) foreach {file =>
			IO.copyFile(file, new File("deploy/" + file.getName()))
		}
	}
}

deploy <<= deploy dependsOn assembly
