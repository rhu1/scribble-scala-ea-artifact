package tmp.EATmp.RobotProto

import ea.runtime.{Actor, Done, Session}

trait ActorD extends Actor {

	def registerD[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, D1Suspend) => Done.type): Unit = {
		val g = (sid: Session.Sid) => D1Suspend(sid, "D", this)
		enqueueRegisterForPeers(apHost, apPort, "RobotProto", "D", port, d, f, g, Set("R", "W"))
	}
}

case class D1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, D1) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: D1 =
			if (op == "WantD") {
				val s = D2(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				WantDD(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "D", "R", g)
		Done
	}
}

sealed trait D1 extends Session.IState

case class WantDD(sid: Session.Sid, role: Session.Role, x1: String, s: D2) extends D1

case class EndD(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

	override def finish(): Done.type = {
		checkNotUsed()
		val done = super.finish()
		actor.end(sid, "D")
		done
	}
}

case class D2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendBusy(x1: String): D3 = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "D", "R", "Busy", pay)
		D3(sid, "D", actor)
	}

	def sendGoIn(x1: String): D4 = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "D", "R", "GoIn", pay)
		D4(sid, "D", actor)
	}
}

case class D3(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendCancel(x1: String): EndD = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "D", "W", "Cancel", pay)
		EndD(sid, "D", actor)
	}
}

case class D4(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendPrepare(x1: String): D5Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "D", "W", "Prepare", pay)
		D5Suspend(sid, "D", actor)
	}
}

case class D5Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, D5) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: D5 =
			if (op == "Inside") {
				val s = D6Suspend(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				InsideD(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "D", "R", g)
		Done
	}
}

sealed trait D5 extends Session.IState

case class InsideD(sid: Session.Sid, role: Session.Role, x1: String, s: D6Suspend) extends D5

case class D6Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, D6) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: D6 =
			if (op == "Prepared") {
				val s = D7(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				PreparedD(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "D", "W", g)
		Done
	}
}

sealed trait D6 extends Session.IState

case class PreparedD(sid: Session.Sid, role: Session.Role, x1: String, s: D7) extends D6

case class D7(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendDeliver(x1: String): D8Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "D", "W", "Deliver", pay)
		D8Suspend(sid, "D", actor)
	}
}

case class D8Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, D8) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: D8 =
			if (op == "WantLeave") {
				val s = D9(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				WantLeaveD(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "D", "R", g)
		Done
	}
}

sealed trait D8 extends Session.IState

case class WantLeaveD(sid: Session.Sid, role: Session.Role, x1: String, s: D9) extends D8

case class D9(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendGoOut(x1: String): D10Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "D", "R", "GoOut", pay)
		D10Suspend(sid, "D", actor)
	}
}

case class D10Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, D10) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: D10 =
			if (op == "Outside") {
				val s = D11Suspend(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				OutsideD(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "D", "R", g)
		Done
	}
}

sealed trait D10 extends Session.IState

case class OutsideD(sid: Session.Sid, role: Session.Role, x1: String, s: D11Suspend) extends D10

case class D11Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, D11) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: D11 =
			if (op == "TableIdle") {
				val s = EndD(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				TableIdleD(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "D", "W", g)
		Done
	}
}

sealed trait D11 extends Session.IState

case class TableIdleD(sid: Session.Sid, role: Session.Role, x1: String, s: EndD) extends D11