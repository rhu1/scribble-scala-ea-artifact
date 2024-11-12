package tmp.EATmp.Fib2

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorP extends Actor {

    def registerP[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, P1) => Done.type): Unit = {
        val g = (sid: Session.Sid) => P1(sid, "P", this)
        enqueueRegisterForPeers(apHost, apPort, "Fib2", "P", port, d, f, g, Set("C1", "C2"))
    }
}

case class P1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendRequest1(x1: Int): P2 = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "P", "C1", "Request1", pay)
        P2(sid, "P", actor)
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

case class P2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendRequest2(x1: Int): P3Suspend = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "P", "C2", "Request2", pay)
        P3Suspend(sid, "P", actor)
    }
}

case class P3Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, P3) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: P3 =
            if (op == "Response1") {
                val s = P4Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                Response1P(sid, role, actor.deserializeInt(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "P", "C1", g)
        Done
    }
}

sealed trait P3 extends Session.IState

case class Response1P(sid: Session.Sid, role: Session.Role, x1: Int, s: P4Suspend) extends P3

case class P4Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, P4) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: P4 =
            if (op == "Response2") {
                val s = EndP(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                Response2P(sid, role, actor.deserializeInt(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "P", "C2", g)
        Done
    }
}

sealed trait P4 extends Session.IState

case class Response2P(sid: Session.Sid, role: Session.Role, x1: Int, s: EndP) extends P4