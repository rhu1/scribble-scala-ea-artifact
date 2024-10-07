package tmp.EATmp.ChatProto3

import ea.runtime.{Actor, Done, Session}

import scala.annotation.targetName

trait ActorR extends Actor {

	@targetName("registerR3")
	def registerR[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, R1) => Done.type): Unit = {
		val g = (sid: Session.Sid) => R1(sid, "R", this)
		enqueueRegisterForPeers(apHost, apPort, "ChatProto3", "R", port, d, f, g, Set("C"))
	}
}

case class R1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendIncomingChatMessage(x1: String): R1 = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "R", "C", "IncomingChatMessage", pay)
		R1(sid, "R", actor)
	}

	def sendBye(x1: String): EndR = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "R", "C", "Bye", pay)
		EndR(sid, "R", actor)
	}
}

case class EndR(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

	override def finish(): Done.type = {
		checkNotUsed()
		val done = super.finish()
		actor.end(sid, "R")
		done
	}
}