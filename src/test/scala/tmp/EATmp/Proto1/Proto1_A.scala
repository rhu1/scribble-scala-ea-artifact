package tmp.EATmp.Proto1

import ea.runtime.{Actor, Done, Session}

trait ActorA extends Actor {

	def registerA[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, A1) => Done.type): Unit = {
		val g = (sid: Session.Sid) => A1(sid, this)
		enqueueRegisterForPeers(apHost, apPort, "Proto1", "A", port, d, f, g, Set("B"))
	}
}

case class A1(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendL1(x: String): EndA = {
		checkNotUsed()
		actor.sendMessage(sid, "A", "B", "L1", x)
		EndA(sid, actor)
	}
}

case class EndA(sid: Session.Sid, actor: Actor) extends Session.End[Actor] {

	override def finish(): Done.type = {
		checkNotUsed()
		val done = super.finish()
		actor.end(sid, "A")
		done
	}
}