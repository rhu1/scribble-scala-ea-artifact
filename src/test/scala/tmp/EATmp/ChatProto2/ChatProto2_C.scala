package tmp.EATmp.ChatProto2

import ea.runtime.{Actor, Done, Session}

import scala.annotation.targetName

trait ActorC extends Actor {

	@targetName("registerC2")
	def registerC[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, C1) => Done.type): Unit = {
		val g = (sid: Session.Sid) => C1(sid, this)
		enqueueRegisterForPeers(apHost, apPort, "ChatProto2", "C", port, d, f, g, Set("R"))
	}
}

case class C1(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendOutgoingChatMessage(x1: String): C1 = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "C", "R", "OutgoingChatMessage", pay)
		C1(sid, actor)
	}

	def sendLeaveRoom(x1: String): EndC = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "C", "R", "LeaveRoom", pay)
		EndC(sid, actor)
	}
}

case class EndC(sid: Session.Sid, actor: Actor) extends Session.End[Actor] {

	override def finish(): Done.type = {
		checkNotUsed()
		val done = super.finish()
		actor.end(sid, "C")
		done
	}
}