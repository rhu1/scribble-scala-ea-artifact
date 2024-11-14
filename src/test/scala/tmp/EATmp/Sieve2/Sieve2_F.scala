package tmp.EATmp.Sieve2

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorF extends Actor {

    def registerF[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, F1) => Done.type): Unit = {
        val g = (sid: Session.Sid) => F1(sid, "F", this)
        enqueueRegisterForPeers(apHost, apPort, "Sieve2", "F", port, d, f, g, Set("Fnext"))
    }
}

case class F1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendNewPrime(x1: Int): F2 = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "F", "Fnext", "NewPrime", pay)
        F2(sid, "F", actor)
    }
}

case class EndF(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "F")
        done
    }
}

case class F2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendLongBox2(x1: Int): F2 = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "F", "Fnext", "LongBox2", pay)
        F2(sid, "F", actor)
    }

    @throws[IOException]
    def sendExit2(): F3Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "F", "Fnext", "Exit2", pay)
        F3Suspend(sid, "F", actor)
    }
}

case class F3Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, F3) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: F3 =
            if (op == "Ack2") {
                val s = EndF(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                Ack2F(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "F", "Fnext", g)
        Done
    }
}

sealed trait F3 extends Session.IState

case class Ack2F(sid: Session.Sid, role: Session.Role, s: EndF) extends F3