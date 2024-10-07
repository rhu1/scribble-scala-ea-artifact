package tmp.EATmp.ChatProto3

import ea.runtime.{Actor, Done, Session}

trait ActorC extends Actor {

	def registerC[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, C1Suspend) => Done.type): Unit = {
		val g = (sid: Session.Sid) => C1Suspend(sid, this)
		enqueueRegisterForPeers(apHost, apPort, "ChatProto3", "C", port, d, f, g, Set("R"))
	}
}

case class C1Suspend(sid: Session.Sid, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, C1) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: C1 =
			if (op == "IncomingChatMessage") {
				val s = C1Suspend(sid, actor)
				succ = Some(s)
				val split = pay.split("::::")
				IncomingChatMessageC(sid, actor.deserializeString(split(0)), s)
			} else 	if (op == "Bye") {
				val s = EndC(sid, actor)
				succ = Some(s)
				val split = pay.split("::::")
				ByeC(sid, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "C", "R", g)
		Done
	}
}

sealed trait C1 extends Session.IState

case class IncomingChatMessageC(sid: Session.Sid, x1: String, s: C1Suspend) extends C1

case class ByeC(sid: Session.Sid, x1: String, s: EndC) extends C1

case class EndC(sid: Session.Sid, actor: Actor) extends Session.End[Actor] {

	override def finish(): Done.type = {
		checkNotUsed()
		val done = super.finish()
		actor.end(sid, "C")
		done
	}
}