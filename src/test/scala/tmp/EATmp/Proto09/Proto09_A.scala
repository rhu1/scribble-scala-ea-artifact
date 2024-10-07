package tmp.EATmp.Proto09

import ea.runtime.{Actor, Done, Session}

trait ActorA extends Actor {

	def registerA[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, A1) => Done.type): Unit = {
		val g = (sid: Session.Sid) => A1(sid, "A", this)
		enqueueRegisterForPeers(apHost, apPort, "Proto09", "A", port, d, f, g, Set("B"))
	}
}

case class A1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendL1(): A2Suspend = {
		checkNotUsed()
		val pay = ""
		actor.sendMessage(sid, "A", "B", "L1", pay)
		A2Suspend(sid, "A", actor)
	}
}

case class EndA(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

	override def finish(): Done.type = {
		checkNotUsed()
		val done = super.finish()
		actor.end(sid, "A")
		done
	}
}

case class A2Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, A2) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: A2 =
			if (op == "L2") {
				val s = EndA(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				L2A(sid, role, actor.deserializeInt(split(0)), actor.deserializeBoolean(split(1)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "A", "B", g)
		Done
	}
}

sealed trait A2 extends Session.IState

case class L2A(sid: Session.Sid, role: Session.Role, x1: Int, x2: Boolean, s: EndA) extends A2