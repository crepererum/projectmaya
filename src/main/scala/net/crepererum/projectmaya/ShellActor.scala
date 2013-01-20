import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.IO
import akka.actor.IOManager
import akka.util.ByteString

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.security.SecureRandom

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.IMain

package net.crepererum.projectmaya {
	class ShellActor extends Actor {
		val port = 5555
		val random = new SecureRandom
		val socket = IOManager(context.system).listen("localhost", port)
		val state = IO.IterateeRef.Map.async[IO.Handle]()(context.dispatcher)
		val pin = generatePin

		def receive = {
			case IO.Listening(server, address) => println(s"Shell loaded @ $address (PIN: $pin)")
			case IO.NewClient(server) => {
				val socket = server.accept()
				state(socket) flatMap (_ => ShellReader.process(socket, context.system, pin))
			}
			case IO.Read(socket, bytes) => state(socket)(IO Chunk bytes)
			case IO.Closed(socket, bytes) => {
				state(socket)(IO.EOF)
				state -= socket
			}
		}

		def generatePin = {
			val pool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
			val bytes = new Array[Byte](16)
			random nextBytes bytes
			val carray = bytes map (byte => pool charAt (byte & (pool.length() - 1)))
			new String(carray)
		}
	}

	trait ReplHelperTrait

	object ShellReader {
		val NEWLINE = "\r\n"
		val PROMPT = ">> "

		def process(socket: IO.SocketHandle, system: ActorSystem, pin: String): IO.Iteratee[Unit] = verifyPin(socket, pin) flatMap { x =>
			x match {
				case true => replLoop(socket, system)
				case false => IO Done socket.close()
			}
		}

		def verifyPin(socket: IO.SocketHandle, pin: String) = {
			socket write ByteString("PIN: ")
			readCommand map { s => s == pin }
		}

		def replLoop(socket: IO.SocketHandle, system: ActorSystem) = {
			val settings = new Settings
			settings.usejavacp.value = true
			settings.embeddedDefaults[ReplHelperTrait]
			val writer = new StringWriter()
			val repl = new IMain(settings, new PrintWriter(writer))

			repl beQuietDuring {
				repl bind ("_exit", (() => socket.close))
				repl bind ("out", new PrintStream(new SocketOutStream(socket)))
				repl bind ("system", system)

				repl interpret ("def exit = _exit()")
				repl interpret ("import out.{format, print, printf, println}")
				repl interpret ("import net.crepererum.projectmaya._")
			}

			socket write ByteString(PROMPT)

			IO repeat {
				for (command <- readCommand) yield {
					repl.interpret(command)
					socket write ByteString(writer.toString().trim() + NEWLINE)
					writer.getBuffer().setLength(0) // clear writer
					socket write ByteString(PROMPT)
				}
			}
		}

		def readCommand = for (raw <- IO takeUntil ByteString(NEWLINE)) yield raw.decodeString("UTF-8")
	}

	class SocketOutStream(socket: IO.SocketHandle) extends OutputStream {
		override def close() { /* ignore */ }

		override def flush() { /* ignore */ }

		override def write(b: Array[Byte]) {
			val buffer = new ByteArrayOutputStream
			buffer.write(b)
			socket write ByteString(buffer.toByteArray())
		}

		override def write(b: Array[Byte], off: Int, len: Int) {
			val buffer = new ByteArrayOutputStream
			buffer.write(b, off, len)
			socket write ByteString(buffer.toByteArray())
		}

		override def write(b: Int) {
			val buffer = new ByteArrayOutputStream
			buffer.write(b)
			socket write ByteString(buffer.toByteArray())
		}
	}
}
