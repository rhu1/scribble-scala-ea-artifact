package ea.example.savina.fib.Fib.Proto2

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorC2 extends Actor {

    def registerC2[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, C21Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => C21Suspend(sid, "C2", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto2", "C2", port, d, f, g, Set("P", "C1"))
    }
}

case class C21Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, C21) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: C21 =
            if (op == "Request2") {
                val s = C22(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                Request2C2(sid, role, actor.deserializeInt(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "C2", "P", g)
        Done
    }
}

sealed trait C21 extends Session.IState

case class Request2C2(sid: Session.Sid, role: Session.Role, x1: Int, s: C22) extends C21

case class EndC2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "C2")
        done
    }
}

case class C22(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendResponse2(x1: Int): EndC2 = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "C2", "P", "Response2", pay)
        EndC2(sid, "C2", actor)
    }
}