package tmp.EATmp.Proto10

import ea.runtime.{Actor, Done, Session}

trait ActorB extends Actor {

	def registerB[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, B1Suspend) => Done.type): Unit = {
		val g = (sid: Session.Sid) => B1Suspend(sid, this)
		enqueueRegisterForPeers(apHost, apPort, "Proto10", "B", port, d, f, g, Set("A"))
	}
}

case class B1Suspend(sid: Session.Sid, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, B1) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: B1 =
			if (op == "L1") {
				val s = B2(sid, actor)
				succ = Some(s)
				val split = pay.split("::::")
				L1B(sid, s)
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

case class L1B(sid: Session.Sid, s: B2) extends B1

case class B2(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendL2(): B1Suspend = {
		checkNotUsed()
		val pay = ""
		actor.sendMessage(sid, "B", "A", "L2", pay)
		B1Suspend(sid, actor)
	}
}