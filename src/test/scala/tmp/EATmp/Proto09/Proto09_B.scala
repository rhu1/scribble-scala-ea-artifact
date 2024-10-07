package tmp.EATmp.Proto09

import ea.runtime.{Actor, Done, Session}

trait ActorB extends Actor {

	def registerB[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, B1Suspend) => Done.type): Unit = {
		val g = (sid: Session.Sid) => B1Suspend(sid, "B", this)
		enqueueRegisterForPeers(apHost, apPort, "Proto09", "B", port, d, f, g, Set("A"))
	}
}

case class B1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, B1) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: B1 =
			if (op == "L1") {
				val s = B2(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				L1B(sid, role, s)
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

case class L1B(sid: Session.Sid, role: Session.Role, s: B2) extends B1

case class EndB(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

	override def finish(): Done.type = {
		checkNotUsed()
		val done = super.finish()
		actor.end(sid, "B")
		done
	}
}

case class B2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendL2(x1: Int, x2: Boolean): EndB = {
		checkNotUsed()
		val pay = actor.serializeInt(x1) + "::::" + actor.serializeBoolean(x2)
		actor.sendMessage(sid, "B", "A", "L2", pay)
		EndB(sid, "B", actor)
	}
}