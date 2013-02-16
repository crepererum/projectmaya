import AssemblyKeys._

assemblySettings

name := "ProjectMaya"

version := "Day0"

scalaVersion := "2.10.0"

resolvers ++= List(
	"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
	"Sourceforge JSI Repository" at "http://sourceforge.net/projects/jsi/files/m2_repo"
)

libraryDependencies ++= List(
	"com.typesafe.akka" %% "akka-actor" % "2.1.0",
	"org.lwjgl.lwjgl" % "lwjgl" % "2.8.5",
	"net.sourceforge.jsi" % "jsi" % "1.0.0"
)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _ )

LWJGLPlugin.lwjglSettings ++ Seq(
	LWJGLPlugin.lwjgl.version := "2.8.5"
)

javacOptions ++= Seq(
	"-source", "1.7",
	"-target", "1.7"
)

scalacOptions ++= Seq(
	"-deprecation",
	"-feature",
	"-optimize",
	"-target:jvm-1.7",
	"-unchecked",
	"-Xmax-classfile-name", "72"
)

jarName in assembly := "ProjectMaya.jar"

deployDir := new File("deploy/")

deploy <<= (lwjgl.copyDir, deployDir) map {(source, target) =>
	IO.createDirectory(target)
	IO.copyFile(new File("target/ProjectMaya.jar"), new File(target, "ProjectMaya.jar"))
	IO.listFiles(source) foreach {subdir =>
		IO.listFiles(subdir) foreach {file =>
			IO.copyFile(file, new File(target, file.getName()))
		}
	}
	IO.copyDirectory(new File("data"), target)
}

cleanFiles <+= deployDir

deploy <<= deploy dependsOn assembly
