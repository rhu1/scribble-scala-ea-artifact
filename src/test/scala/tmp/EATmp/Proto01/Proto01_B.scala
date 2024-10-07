package tmp.EATmp.Proto01

import ea.runtime.{Actor, Done, Session}

trait ActorB extends Actor {

	def registerB[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, B1Suspend) => Done.type): Unit = {
		val g = (sid: Session.Sid) => B1Suspend(sid, "B", this)
		enqueueRegisterForPeers(apHost, apPort, "Proto01", "B", port, d, f, g, Set("A"))
	}
}

case class B1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, B1) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: B1 =
			if (op == "L1") {
				val s = EndB(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				L1B(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "B", "A", g)
		Done
	}
}

sealed trait B1 extends Session.IState

case class L1B(sid: Session.Sid, role: Session.Role, x1: String, s: EndB) extends B1

case class EndB(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

	override def finish(): Done.type = {
		checkNotUsed()
		val done = super.finish()
		actor.end(sid, "B")
		done
	}
}