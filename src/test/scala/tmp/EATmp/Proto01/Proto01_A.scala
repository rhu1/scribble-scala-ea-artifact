package tmp.EATmp.Proto01

import ea.runtime.{Actor, Done, Session}

trait ActorA extends Actor {

	def registerA[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, A1) => Done.type): Unit = {
		val g = (sid: Session.Sid) => A1(sid, "A", this)
		enqueueRegisterForPeers(apHost, apPort, "Proto01", "A", port, d, f, g, Set("B"))
	}
}

case class A1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

	def sendL1(x1: String): EndA = {
		checkNotUsed()
		val pay = actor.serializeString(x1)
		actor.sendMessage(sid, "A", "B", "L1", pay)
		EndA(sid, "A", actor)
	}
}

case class EndA(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

	override def finish(): Done.type = {
		checkNotUsed()
		val done = super.finish()
		actor.end(sid, "A")
		done
	}
}