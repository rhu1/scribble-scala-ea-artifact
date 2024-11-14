package tmp.EATmp.Sieve1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorF1 extends Actor {

    def registerF1[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, F11Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => F11Suspend(sid, "F1", this)
        enqueueRegisterForPeers(apHost, apPort, "Sieve1", "F1", port, d, f, g, Set("M", "G"))
    }
}

case class F11Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, F11) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: F11 =
            if (op == "NewPrime") {
                val s = F12Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                NewPrimeF1(sid, role, actor.deserializeInt(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "F1", "G", g)
        Done
    }
}

sealed trait F11 extends Session.IState

case class NewPrimeF1(sid: Session.Sid, role: Session.Role, x1: Int, s: F12Suspend) extends F11

case class EndF1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "F1")
        done
    }
}

case class F12Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, F12) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: F12 =
            if (op == "LongBox") {
                val s = F12Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                LongBoxF1(sid, role, actor.deserializeInt(split(0)), s)
            } else     if (op == "Exit") {
                val s = F13(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                ExitF1(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "F1", "G", g)
        Done
    }
}

sealed trait F12 extends Session.IState

case class LongBoxF1(sid: Session.Sid, role: Session.Role, x1: Int, s: F12Suspend) extends F12

case class ExitF1(sid: Session.Sid, role: Session.Role, s: F13) extends F12

case class F13(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendAck(): EndF1 = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "F1", "G", "Ack", pay)
        EndF1(sid, "F1", actor)
    }
}