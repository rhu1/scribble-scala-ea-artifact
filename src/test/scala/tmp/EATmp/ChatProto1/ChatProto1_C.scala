package tmp.EATmp.ChatProto1

import ea.runtime.{Actor, Done, Session}

import scala.annotation.targetName

trait ActorC extends Actor {
	
	@targetName("registerC1")
	def registerC[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, C1) => Done.type): Unit = {
		val g = (sid: Session.Sid) => C1(sid, "C", this)
		enqueueRegisterForPeers(apHost, apPort, "ChatProto1", "C", port, d, f, g, Set("S"))
	}
}

case class C1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendLookupRoom(x1: String): C2Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "C", "S", "LookupRoom", pay)
		C2Suspend(sid, "C", actor)
	}

	def sendCreateRoom(x1: String): C3Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "C", "S", "CreateRoom", pay)
		C3Suspend(sid, "C", actor)
	}

	def sendListRooms(x1: String): C4Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "C", "S", "ListRooms", pay)
		C4Suspend(sid, "C", actor)
	}

	def sendBye(x1: String): EndC = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "C", "S", "Bye", pay)
		EndC(sid, "C", actor)
	}
}

case class EndC(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

	override def finish(): Done.type = {
		checkNotUsed()
		val done = super.finish()
		actor.end(sid, "C")
		done
	}
}

case class C2Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, C2) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: C2 =
			if (op == "RoomPID") {
				val s = C1(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				RoomPIDC(sid, role, actor.deserializeString(split(0)), s)
			} else 	if (op == "RoomNotFound") {
				val s = C1(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				RoomNotFoundC(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "C", "S", g)
		Done
	}
}

sealed trait C2 extends Session.IState

case class RoomPIDC(sid: Session.Sid, role: Session.Role, x1: String, s: C1) extends C2

case class RoomNotFoundC(sid: Session.Sid, role: Session.Role, x1: String, s: C1) extends C2

case class C3Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, C3) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: C3 =
			if (op == "CreateRoomSuccess") {
				val s = C1(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				CreateRoomSuccessC(sid, role, actor.deserializeString(split(0)), s)
			} else 	if (op == "RoomExists") {
				val s = C1(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				RoomExistsC(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "C", "S", g)
		Done
	}
}

sealed trait C3 extends Session.IState

case class CreateRoomSuccessC(sid: Session.Sid, role: Session.Role, x1: String, s: C1) extends C3

case class RoomExistsC(sid: Session.Sid, role: Session.Role, x1: String, s: C1) extends C3

case class C4Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, C4) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: C4 =
			if (op == "RoomList") {
				val s = C1(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				RoomListC(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "C", "S", g)
		Done
	}
}

sealed trait C4 extends Session.IState

case class RoomListC(sid: Session.Sid, role: Session.Role, x1: String, s: C1) extends C4