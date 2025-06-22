package ea.example.savina.diningself.DiningSelf.Proto1

import ea.runtime.{Actor, Done, Session}

trait ActorP1 extends Actor {

    def registerP1[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, P11Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => P11Suspend(sid, "P1", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto1", "P1", port, d, f, g, Set("M"))
    }
}

case class P11Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, P11) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: P11 =
            if (op == "Start") {
                val s = EndP1(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                StartP1(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "P1", "M", g)
        Done
    }
}

sealed trait P11 extends Session.IState

case class StartP1(sid: Session.Sid, role: Session.Role, s: EndP1) extends P11

case class EndP1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "P1")
        done
    }
}