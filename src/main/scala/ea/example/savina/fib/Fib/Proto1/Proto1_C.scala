package ea.example.savina.fib.Fib.Proto1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorC extends Actor {

    def registerC[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, C1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => C1Suspend(sid, "C", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto1", "C", port, d, f, g, Set("P"))
    }
}

case class C1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, C1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: C1 =
            if (op == "Request") {
                val s = C2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                RequestC(sid, role, actor.deserializeInt(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "C", "P", g)
        Done
    }
}

sealed trait C1 extends Session.IState

case class RequestC(sid: Session.Sid, role: Session.Role, x1: Int, s: C2) extends C1

case class EndC(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "C")
        done
    }
}

case class C2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendResponse(x1: Int): EndC = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "C", "P", "Response", pay)
        EndC(sid, "C", actor)
    }
}