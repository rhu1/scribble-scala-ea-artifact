package tmp.EATmp.ShopProto1

import ea.runtime.{Actor, Done, Session}

trait ActorC extends Actor {

	def registerC[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, C1) => Done.type): Unit = {
		val g = (sid: Session.Sid) => C1(sid, this)
		enqueueRegisterForPeers(apHost, apPort, "Shop", "C", port, d, f, g, Set("S", "P"))
	}
}

case class C1(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendReqItems(x1: String): C2Suspend = {
		checkNotUsed()
		actor.sendMessage(sid, "C", "S", "ReqItems", x1)
		C2Suspend(sid, actor)
	}
}

case class C2Suspend(sid: Session.Sid, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, C2) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: Object) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: C2 =
			if (op == "Items") {
				val s = C3(sid, actor)
				succ = Some(s)
				ItemsC(sid, pay.asInstanceOf[String], s)
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

case class ItemsC(sid: Session.Sid, x1: String, s: C3) extends C2

case class C3(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendGetItemInfo(x1: String): C4Suspend = {
		checkNotUsed()
		actor.sendMessage(sid, "C", "S", "GetItemInfo", x1)
		C4Suspend(sid, actor)
	}

	def sendCheckout(x1: String): C5Suspend = {
		checkNotUsed()
		actor.sendMessage(sid, "C", "S", "Checkout", x1)
		C5Suspend(sid, actor)
	}
}

case class C4Suspend(sid: Session.Sid, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, C4) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: Object) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: C4 =
			if (op == "ItemInfo") {
				val s = C3(sid, actor)
				succ = Some(s)
				ItemInfoC(sid, pay.asInstanceOf[String], s)
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

case class ItemInfoC(sid: Session.Sid, x1: String, s: C3) extends C4

case class C5Suspend(sid: Session.Sid, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, C5) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: Object) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: C5 =
			if (op == "Processing") {
				val s = C6Suspend(sid, actor)
				succ = Some(s)
				ProcessingC(sid, pay.asInstanceOf[String], s)
			} else 	if (op == "OutOfStock") {
				val s = C3(sid, actor)
				succ = Some(s)
				OutOfStockC(sid, pay.asInstanceOf[String], s)
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

sealed trait C5 extends Session.IState

case class ProcessingC(sid: Session.Sid, x1: String, s: C6Suspend) extends C5

case class OutOfStockC(sid: Session.Sid, x1: String, s: C3) extends C5

case class C6Suspend(sid: Session.Sid, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, C6) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: Object) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: C6 =
			if (op == "OKc") {
				val s = C3(sid, actor)
				succ = Some(s)
				OKcC(sid, pay.asInstanceOf[String], s)
			} else 	if (op == "Declinedc") {
				val s = C3(sid, actor)
				succ = Some(s)
				DeclinedcC(sid, pay.asInstanceOf[String], s)
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

sealed trait C6 extends Session.IState

case class OKcC(sid: Session.Sid, x1: String, s: C3) extends C6

case class DeclinedcC(sid: Session.Sid, x1: String, s: C3) extends C6