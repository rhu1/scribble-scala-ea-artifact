package tmp.EATmp.ShopProto2

import ea.runtime.{Actor, Done, Session}

trait ActorSS extends Actor {

	def registerSS[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, SS1) => Done.type): Unit = {
		val g = (sid: Session.Sid) => SS1(sid, "SS", this)
		enqueueRegisterForPeers(apHost, apPort, "ShopProto2", "SS", port, d, f, g, Set("SF"))
	}
}

case class SS1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendAddItem(x1: String): SS1 = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "SS", "SF", "AddItem", pay)
		SS1(sid, "SS", actor)
	}

	def sendRemoveItem(x1: String): SS1 = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "SS", "SF", "RemoveItem", pay)
		SS1(sid, "SS", actor)
	}
}