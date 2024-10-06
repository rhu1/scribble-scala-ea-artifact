package tmp.EATmp.ShopProto2

import ea.runtime.{Actor, Done, Session}

trait ActorSS extends Actor {

	def registerSS[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, SS1) => Done.type): Unit = {
		val g = (sid: Session.Sid) => SS1(sid, this)
		enqueueRegisterForPeers(apHost, apPort, "ShopProto2", "SS", port, d, f, g, Set("SF"))
	}
}

case class SS1(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendAddItem(x1: String): SS1 = {
		checkNotUsed()
		actor.sendMessage(sid, "SS", "SF", "AddItem", x1)
		SS1(sid, actor)
	}

	def sendRemoveItem(x1: String): SS1 = {
		checkNotUsed()
		actor.sendMessage(sid, "SS", "SF", "RemoveItem", x1)
		SS1(sid, actor)
	}
}