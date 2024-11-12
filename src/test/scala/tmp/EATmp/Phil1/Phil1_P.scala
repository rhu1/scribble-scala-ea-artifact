package tmp.EATmp.Phil1

import ea.runtime.{Actor, Done, Session}

trait ActorP extends Actor {

    def registerP[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, P1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => P1Suspend(sid, "P", this)
        enqueueRegisterForPeers(apHost, apPort, "Phil1", "P", port, d, f, g, Set("M"))
    }
}

case class P1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, P1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: P1 =
            if (op == "Start") {
                val s = EndP(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                StartP(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "P", "M", g)
        Done
    }
}

sealed trait P1 extends Session.IState

case class StartP(sid: Session.Sid, role: Session.Role, s: EndP) extends P1

case class EndP(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "P")
        done
    }
}