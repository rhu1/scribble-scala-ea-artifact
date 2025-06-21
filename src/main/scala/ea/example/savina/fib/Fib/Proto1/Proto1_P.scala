package ea.example.savina.fib.Fib.Proto1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorP extends Actor {

    def registerP[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, P1) => Done.type): Unit = {
        val g = (sid: Session.Sid) => P1(sid, "P", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto1", "P", port, d, f, g, Set("C"))
    }
}

case class P1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendRequest(x1: Int): P2Suspend = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "P", "C", "Request", pay)
        P2Suspend(sid, "P", actor)
    }
}

case class EndP(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "P")
        done
    }
}

case class P2Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, P2) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: P2 =
            if (op == "Response") {
                val s = EndP(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                ResponseP(sid, role, actor.deserializeInt(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "P", "C", g)
        Done
    }
}

sealed trait P2 extends Session.IState

case class ResponseP(sid: Session.Sid, role: Session.Role, x1: Int, s: EndP) extends P2