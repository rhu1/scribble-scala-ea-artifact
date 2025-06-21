package ea.example.chat.Chat.Proto3

import ea.runtime.{Actor, Done, Session}

trait ActorC3 extends Actor {

    def registerC3[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, C31Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => C31Suspend(sid, "C3", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto3", "C3", port, d, f, g, Set("R3"))
    }
}

case class C31Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, C31) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: C31 =
            if (op == "IncomingChatMessage") {
                val s = C31Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                IncomingChatMessageC3(sid, role, actor.deserializeString(split(0)), s)
            } else     if (op == "Bye") {
                val s = EndC3(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                ByeC3(sid, role, actor.deserializeString(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "C3", "R3", g)
        Done
    }
}

sealed trait C31 extends Session.IState

case class IncomingChatMessageC3(sid: Session.Sid, role: Session.Role, x1: String, s: C31Suspend) extends C31

case class ByeC3(sid: Session.Sid, role: Session.Role, x1: String, s: EndC3) extends C31

case class EndC3(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "C3")
        done
    }
}