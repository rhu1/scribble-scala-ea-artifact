package tmp.EATmp.ShopProto1

import ea.runtime.{Actor, Done, Session}

trait ActorS extends Actor {

	def registerS[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, S1Suspend) => Done.type): Unit = {
		val g = (sid: Session.Sid) => S1Suspend(sid, "S", this)
		enqueueRegisterForPeers(apHost, apPort, "ShopProto1", "S", port, d, f, g, Set("C", "P"))
	}
}

case class S1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, S1) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: S1 =
			if (op == "ReqItems") {
				val s = S2(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				ReqItemsS(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "S", "C", g)
		Done
	}
}

sealed trait S1 extends Session.IState

case class ReqItemsS(sid: Session.Sid, role: Session.Role, x1: String, s: S2) extends S1

case class S2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendItems(x1: String): S3Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "S", "C", "Items", pay)
		S3Suspend(sid, "S", actor)
	}
}

case class S3Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, S3) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: S3 =
			if (op == "GetItemInfo") {
				val s = S4(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				GetItemInfoS(sid, role, actor.deserializeString(split(0)), s)
			} else 	if (op == "Checkout") {
				val s = S5(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				CheckoutS(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "S", "C", g)
		Done
	}
}

sealed trait S3 extends Session.IState

case class GetItemInfoS(sid: Session.Sid, role: Session.Role, x1: String, s: S4) extends S3

case class CheckoutS(sid: Session.Sid, role: Session.Role, x1: String, s: S5) extends S3

case class S4(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendItemInfo(x1: String): S3Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "S", "C", "ItemInfo", pay)
		S3Suspend(sid, "S", actor)
	}
}

case class S5(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendProcessing(x1: String): S6 = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "S", "C", "Processing", pay)
		S6(sid, "S", actor)
	}

	def sendOutOfStock(x1: String): S3Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "S", "C", "OutOfStock", pay)
		S3Suspend(sid, "S", actor)
	}
}

case class S6(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendBuy(x1: String): S7Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "S", "P", "Buy", pay)
		S7Suspend(sid, "S", actor)
	}
}

case class S7Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, S7) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: String) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: S7 =
			if (op == "OK") {
				val s = S8(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				OKS(sid, role, actor.deserializeString(split(0)), s)
			} else 	if (op == "Declined") {
				val s = S9(sid, role, actor)
				succ = Some(s)
				val split = pay.split("::::")
				DeclinedS(sid, role, actor.deserializeString(split(0)), s)
			} else {
				throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
			}
			val done = f.apply(d, msg)
			succ.get.checkUsed()
			done
		}
		actor.setHandler(sid, "S", "P", g)
		Done
	}
}

sealed trait S7 extends Session.IState

case class OKS(sid: Session.Sid, role: Session.Role, x1: String, s: S8) extends S7

case class DeclinedS(sid: Session.Sid, role: Session.Role, x1: String, s: S9) extends S7

case class S8(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendOKc(x1: String): S3Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "S", "C", "OKc", pay)
		S3Suspend(sid, "S", actor)
	}
}

case class S9(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendDeclinedc(x1: String): S3Suspend = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "S", "C", "Declinedc", pay)
		S3Suspend(sid, "S", actor)
	}
}