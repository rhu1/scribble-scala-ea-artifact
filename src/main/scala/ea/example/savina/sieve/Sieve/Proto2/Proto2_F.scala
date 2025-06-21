package ea.example.savina.sieve.Sieve.Proto2

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorF extends Actor {

    def registerF[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, F1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => F1Suspend(sid, "F", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto2", "F", port, d, f, g, Set("Fnext"))
    }
}

case class F1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, F1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: F1 =
            if (op == "Ready") {
                val s = F2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                ReadyF(sid, role, s)
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

sealed trait F1 extends Session.IState

case class ReadyF(sid: Session.Sid, role: Session.Role, s: F2) extends F1

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
    def sendNewPrime(x1: Int): F3 = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "F", "Fnext", "NewPrime", pay)
        F3(sid, "F", actor)
    }
}

case class F3(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendLongBox2(x1: Int): F3 = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "F", "Fnext", "LongBox2", pay)
        F3(sid, "F", actor)
    }

    @throws[IOException]
    def sendExit2(): F4Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "F", "Fnext", "Exit2", pay)
        F4Suspend(sid, "F", actor)
    }
}

case class F4Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, F4) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: F4 =
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

sealed trait F4 extends Session.IState

case class Ack2F(sid: Session.Sid, role: Session.Role, s: EndF) extends F4