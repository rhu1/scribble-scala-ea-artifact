package ea.example.savina.fib.Fib.Proto2

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorC1 extends Actor {

    def registerC1[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, C11Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => C11Suspend(sid, "C1", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto2", "C1", port, d, f, g, Set("P", "C2"))
    }
}

case class C11Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, C11) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: C11 =
            if (op == "Request1") {
                val s = C12(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                Request1C1(sid, role, actor.deserializeInt(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "C1", "P", g)
        Done
    }
}

sealed trait C11 extends Session.IState

case class Request1C1(sid: Session.Sid, role: Session.Role, x1: Int, s: C12) extends C11

case class EndC1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "C1")
        done
    }
}

case class C12(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendResponse1(x1: Int): EndC1 = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "C1", "P", "Response1", pay)
        EndC1(sid, "C1", actor)
    }
}