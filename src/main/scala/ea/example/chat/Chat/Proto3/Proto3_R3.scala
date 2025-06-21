package ea.example.chat.Chat.Proto3

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorR3 extends Actor {

    def registerR3[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, R31) => Done.type): Unit = {
        val g = (sid: Session.Sid) => R31(sid, "R3", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto3", "R3", port, d, f, g, Set("C3"))
    }
}

case class R31(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendIncomingChatMessage(x1: String): R31 = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "R3", "C3", "IncomingChatMessage", pay)
        R31(sid, "R3", actor)
    }

    @throws[IOException]
    def sendBye(x1: String): EndR3 = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "R3", "C3", "Bye", pay)
        EndR3(sid, "R3", actor)
    }
}

case class EndR3(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "R3")
        done
    }
}