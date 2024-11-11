package tmp.EATmp.PingPong1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorC extends Actor {

    def registerC[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, C1) => Done.type): Unit = {
        val g = (sid: Session.Sid) => C1(sid, "C", this)
        enqueueRegisterForPeers(apHost, apPort, "PingPong1", "C", port, d, f, g, Set("Pinger", "Ponger", "PongReceiver"))
    }
}

case class C1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendStart(): C2Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "C", "Pinger", "Start", pay)
        C2Suspend(sid, "C", actor)
    }
}

case class EndC(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "C")
        done
    }
}

case class C2Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, C2) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: C2 =
            if (op == "Stop") {
                val s = EndC(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                StopC(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "C", "Pinger", g)
        Done
    }
}

sealed trait C2 extends Session.IState

case class StopC(sid: Session.Sid, role: Session.Role, s: EndC) extends C2