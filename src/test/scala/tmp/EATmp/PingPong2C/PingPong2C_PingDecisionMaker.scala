package tmp.EATmp.PingPong2C

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorPingDecisionMaker extends Actor {

    def registerPingDecisionMaker[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, PingDecisionMaker1) => Done.type): Unit = {
        val g = (sid: Session.Sid) => PingDecisionMaker1(sid, "PingDecisionMaker", this)
        enqueueRegisterForPeers(apHost, apPort, "PingPong2C", "PingDecisionMaker", port, d, f, g, Set("C", "PingDecisionReceiver"))
    }
}

case class PingDecisionMaker1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendPing(): PingDecisionMaker1 = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "PingDecisionMaker", "PingDecisionReceiver", "Ping", pay)
        PingDecisionMaker1(sid, "PingDecisionMaker", actor)
    }

    @throws[IOException]
    def sendStopC(): PingDecisionMaker2 = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "PingDecisionMaker", "PingDecisionReceiver", "StopC", pay)
        PingDecisionMaker2(sid, "PingDecisionMaker", actor)
    }
}

case class EndPingDecisionMaker(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "PingDecisionMaker")
        done
    }
}

case class PingDecisionMaker2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendStopC(): EndPingDecisionMaker = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "PingDecisionMaker", "C", "StopC", pay)
        EndPingDecisionMaker(sid, "PingDecisionMaker", actor)
    }
}