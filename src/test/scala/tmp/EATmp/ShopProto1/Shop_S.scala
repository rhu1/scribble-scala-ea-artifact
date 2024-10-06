package tmp.EATmp.ShopProto1

import ea.runtime.{Actor, Done, Session}

trait ActorS extends Actor {

	def registerS[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, S1Suspend) => Done.type): Unit = {
		val g = (sid: Session.Sid) => S1Suspend(sid, this)
		enqueueRegisterForPeers(apHost, apPort, "Shop", "S", port, d, f, g, Set("C", "P"))
	}
}

case class S1Suspend(sid: Session.Sid, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, S1) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: Object) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: S1 =
			if (op == "ReqItems") {
				val s = S2(sid, actor)
				succ = Some(s)
				ReqItemsS(sid, pay.asInstanceOf[String], s)
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

case class ReqItemsS(sid: Session.Sid, x1: String, s: S2) extends S1

case class S2(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendItems(x1: String): S3Suspend = {
		checkNotUsed()
		actor.sendMessage(sid, "S", "C", "Items", x1)
		S3Suspend(sid, actor)
	}
}

case class S3Suspend(sid: Session.Sid, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, S3) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: Object) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: S3 =
			if (op == "GetItemInfo") {
				val s = S4(sid, actor)
				succ = Some(s)
				GetItemInfoS(sid, pay.asInstanceOf[String], s)
			} else 	if (op == "Checkout") {
				val s = S5(sid, actor)
				succ = Some(s)
				CheckoutS(sid, pay.asInstanceOf[String], s)
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

case class GetItemInfoS(sid: Session.Sid, x1: String, s: S4) extends S3

case class CheckoutS(sid: Session.Sid, x1: String, s: S5) extends S3

case class S4(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendItemInfo(x1: String): S3Suspend = {
		checkNotUsed()
		actor.sendMessage(sid, "S", "C", "ItemInfo", x1)
		S3Suspend(sid, actor)
	}
}

case class S5(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendProcessing(x1: String): S6 = {
		checkNotUsed()
		actor.sendMessage(sid, "S", "C", "Processing", x1)
		S6(sid, actor)
	}

	def sendOutOfStock(x1: String): S3Suspend = {
		checkNotUsed()
		actor.sendMessage(sid, "S", "C", "OutOfStock", x1)
		S3Suspend(sid, actor)
	}
}

case class S6(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendBuy(x1: String): S7Suspend = {
		checkNotUsed()
		actor.sendMessage(sid, "S", "P", "Buy", x1)
		S7Suspend(sid, actor)
	}
}

case class S7Suspend(sid: Session.Sid, actor: Actor) extends Session.SuspendState[Actor] {

	def suspend[D <: Session.Data](d: D, f: (D, S7) => Done.type): Done.type = {
		checkNotUsed()
		val g = (op: String, pay: Object) => {
			var succ: Option[Session.ActorState[Actor]] = None
			val msg: S7 =
			if (op == "OK") {
				val s = S8(sid, actor)
				succ = Some(s)
				OKS(sid, pay.asInstanceOf[String], s)
			} else 	if (op == "Declined") {
				val s = S9(sid, actor)
				succ = Some(s)
				DeclinedS(sid, pay.asInstanceOf[String], s)
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

case class OKS(sid: Session.Sid, x1: String, s: S8) extends S7

case class DeclinedS(sid: Session.Sid, x1: String, s: S9) extends S7

case class S8(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendOKc(x1: String): S3Suspend = {
		checkNotUsed()
		actor.sendMessage(sid, "S", "C", "OKc", x1)
		S3Suspend(sid, actor)
	}
}

case class S9(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendDeclinedc(x1: String): S3Suspend = {
		checkNotUsed()
		actor.sendMessage(sid, "S", "C", "Declinedc", x1)
		S3Suspend(sid, actor)
	}
}