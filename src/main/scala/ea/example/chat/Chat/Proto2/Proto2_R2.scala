package ea.example.chat.Chat.Proto2

import ea.runtime.{Actor, Done, Session}

trait ActorR2 extends Actor {

    def registerR2[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, R21Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => R21Suspend(sid, "R2", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto2", "R2", port, d, f, g, Set("C2"))
    }
}

case class R21Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, R21) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: R21 =
            if (op == "OutgoingChatMessage") {
                val s = R21Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                OutgoingChatMessageR2(sid, role, actor.deserializeString(split(0)), s)
            } else     if (op == "LeaveRoom") {
                val s = EndR2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                LeaveRoomR2(sid, role, actor.deserializeString(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "R2", "C2", g)
        Done
    }
}

sealed trait R21 extends Session.IState

case class OutgoingChatMessageR2(sid: Session.Sid, role: Session.Role, x1: String, s: R21Suspend) extends R21

case class LeaveRoomR2(sid: Session.Sid, role: Session.Role, x1: String, s: EndR2) extends R21

case class EndR2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "R2")
        done
    }
}