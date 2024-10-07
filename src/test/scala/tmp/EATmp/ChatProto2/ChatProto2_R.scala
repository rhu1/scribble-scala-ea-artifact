package tmp.EATmp.ChatProto2

import ea.runtime.{Actor, Done, Session}

import scala.annotation.targetName

trait ActorR extends Actor {

	@targetName("registerR2")
	def registerR[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, R1Suspend) => Done.type): Unit = {
		val g = (sid: Session.Sid) => R1Suspend(sid, this)
		enqueueRegisterForPeers(apHost, apPort, "ChatProto2", "R", port, d, f, g, Set("C"))
	}
}

case class R1Suspend(sid: Session.Sid, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, R1) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: R1 =
			if (op == "OutgoingChatMessage") {
				val s = R1Suspend(sid, actor)
				succ = Some(s)
				val split = pay.split("::::")
				OutgoingChatMessageR(sid, actor.deserializeString(split(0)), s)
			} else 	if (op == "LeaveRoom") {
				val s = EndR(sid, actor)
				succ = Some(s)
				val split = pay.split("::::")
				LeaveRoomR(sid, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "R", "C", g)
		Done
	}
}

sealed trait R1 extends Session.IState

case class OutgoingChatMessageR(sid: Session.Sid, x1: String, s: R1Suspend) extends R1

case class LeaveRoomR(sid: Session.Sid, x1: String, s: EndR) extends R1

case class EndR(sid: Session.Sid, actor: Actor) extends Session.End[Actor] {

	override def finish(): Done.type = {
		checkNotUsed()
		val done = super.finish()
		actor.end(sid, "R")
		done
	}
}