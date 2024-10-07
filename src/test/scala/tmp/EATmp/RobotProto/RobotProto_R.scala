package tmp.EATmp.RobotProto

import ea.runtime.{Actor, Done, Session}

trait ActorR extends Actor {

	def registerR[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, R1) => Done.type): Unit = {
		val g = (sid: Session.Sid) => R1(sid, "R", this)
		enqueueRegisterForPeers(apHost, apPort, "RobotProto", "R", port, d, f, g, Set("D", "W"))
	}
}

case class R1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendWantD(x1: String): R2 = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "R", "D", "WantD", pay)
		R2(sid, "R", actor)
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

case class R2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendWantW(x1: String): R3Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "R", "W", "WantW", pay)
		R3Suspend(sid, "R", actor)
	}
}

case class R3Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, R3) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: R3 =
			if (op == "Busy") {
				val s = EndR(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				BusyR(sid, role, actor.deserializeString(split(0)), s)
			} else 	if (op == "GoIn") {
				val s = R4(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				GoInR(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "R", "D", g)
		Done
	}
}

sealed trait R3 extends Session.IState

case class BusyR(sid: Session.Sid, role: Session.Role, x1: String, s: EndR) extends R3

case class GoInR(sid: Session.Sid, role: Session.Role, x1: String, s: R4) extends R3

case class R4(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendInside(x1: String): R5Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "R", "D", "Inside", pay)
		R5Suspend(sid, "R", actor)
	}
}

case class R5Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, R5) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: R5 =
			if (op == "Delivered") {
				val s = R6(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				DeliveredR(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "R", "W", g)
		Done
	}
}

sealed trait R5 extends Session.IState

case class DeliveredR(sid: Session.Sid, role: Session.Role, x1: String, s: R6) extends R5

case class R6(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendPartTaken(x1: String): R7 = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "R", "W", "PartTaken", pay)
		R7(sid, "R", actor)
	}
}

case class R7(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendWantLeave(x1: String): R8Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "R", "D", "WantLeave", pay)
		R8Suspend(sid, "R", actor)
	}
}

case class R8Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, R8) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: R8 =
			if (op == "GoOut") {
				val s = R9(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				GoOutR(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "R", "D", g)
		Done
	}
}

sealed trait R8 extends Session.IState

case class GoOutR(sid: Session.Sid, role: Session.Role, x1: String, s: R9) extends R8

case class R9(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendOutside(x1: String): EndR = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "R", "D", "Outside", pay)
		EndR(sid, "R", actor)
	}
}