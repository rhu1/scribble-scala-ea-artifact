package ea.example.chat.Chat.Proto2

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorC2 extends Actor {

    def registerC2[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, C21) => Done.type): Unit = {
        val g = (sid: Session.Sid) => C21(sid, "C2", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto2", "C2", port, d, f, g, Set("R2"))
    }
}

case class C21(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendOutgoingChatMessage(x1: String): C21 = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "C2", "R2", "OutgoingChatMessage", pay)
        C21(sid, "C2", actor)
    }

    @throws[IOException]
    def sendLeaveRoom(x1: String): EndC2 = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "C2", "R2", "LeaveRoom", pay)
        EndC2(sid, "C2", actor)
    }
}

case class EndC2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "C2")
        done
    }
}