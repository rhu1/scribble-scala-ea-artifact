package ea.example.savina.diningself.DiningSelf.Proto3

import ea.runtime.{Actor, Done, Session}

trait ActorS2 extends Actor {

    def registerS2[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, S21Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => S21Suspend(sid, "S2", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto3", "S2", port, d, f, g, Set("S1"))
    }
}

case class S21Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, S21) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: S21 =
            if (op == "SelfStart") {
                val s = S21Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                SelfStartS2(sid, role, s)
            } else     if (op == "SelfExit") {
                val s = EndS2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                SelfExitS2(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "S2", "S1", g)
        Done
    }
}

sealed trait S21 extends Session.IState

case class SelfStartS2(sid: Session.Sid, role: Session.Role, s: S21Suspend) extends S21

case class SelfExitS2(sid: Session.Sid, role: Session.Role, s: EndS2) extends S21

case class EndS2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "S2")
        done
    }
}