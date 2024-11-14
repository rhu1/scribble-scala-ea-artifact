package tmp.EATmp.Sieve1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorM extends Actor {

    def registerM[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, M1) => Done.type): Unit = {
        val g = (sid: Session.Sid) => M1(sid, "M", this)
        enqueueRegisterForPeers(apHost, apPort, "Sieve1", "M", port, d, f, g, Set("G", "F1"))
    }
}

case class M1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendStart(): M2Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "M", "G", "Start", pay)
        M2Suspend(sid, "M", actor)
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

case class M2Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, M2) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: M2 =
            if (op == "Exit") {
                val s = EndM(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                ExitM(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "M", "G", g)
        Done
    }
}

sealed trait M2 extends Session.IState

case class ExitM(sid: Session.Sid, role: Session.Role, s: EndM) extends M2