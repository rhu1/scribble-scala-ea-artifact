package tmp.EATmp.Proto1

import ea.runtime.{Actor, Done, Session}
import tmp.EATmp.Proto01.{A1, EndA}

trait ActorA extends Actor {

	def registerA[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, A1) => Done.type): Unit = {
		val g = (sid: Session.Sid) => A1(sid, this)
		enqueueRegisterForPeers(apHost, apPort, "Proto1", "A", port, d, f, g, Set("B"))
	}
}

case class A1(sid: Session.Sid, actor: Actor) extends Session.OState[Actor] {

	def sendL1(x: String): EndA = {
		checkNotUsed()
		//val ser = actor.serializeString(x)
		val ser = actor.serializeString(x)
		actor.sendMessage(sid, "A", "B", "L1", ser)
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