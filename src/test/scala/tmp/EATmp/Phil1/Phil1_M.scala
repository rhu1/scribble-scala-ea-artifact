package tmp.EATmp.Phil1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorM extends Actor {

    def registerM[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, M1) => Done.type): Unit = {
        val g = (sid: Session.Sid) => M1(sid, "M", this)
        enqueueRegisterForPeers(apHost, apPort, "Phil1", "M", port, d, f, g, Set("P1"))
    }
}

case class M1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendStart(): EndM = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "M", "P1", "Start", pay)
        EndM(sid, "M", actor)
    }
}

case class EndM(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "M")
        done
    }
}