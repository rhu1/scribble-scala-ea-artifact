package ea.example.savina.diningself.DiningSelf.Proto3

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorS1 extends Actor {

    def registerS1[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, S11) => Done.type): Unit = {
        val g = (sid: Session.Sid) => S11(sid, "S1", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto3", "S1", port, d, f, g, Set("S2"))
    }
}

case class S11(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendSelfStart(): S11 = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "S1", "S2", "SelfStart", pay)
        S11(sid, "S1", actor)
    }

    @throws[IOException]
    def sendSelfExit(): EndS1 = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "S1", "S2", "SelfExit", pay)
        EndS1(sid, "S1", actor)
    }
}

case class EndS1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "S1")
        done
    }
}