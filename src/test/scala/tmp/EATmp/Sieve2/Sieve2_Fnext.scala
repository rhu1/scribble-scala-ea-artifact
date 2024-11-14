package tmp.EATmp.Sieve2

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorFnext extends Actor {

    def registerFnext[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, Fnext1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => Fnext1Suspend(sid, "Fnext", this)
        enqueueRegisterForPeers(apHost, apPort, "Sieve2", "Fnext", port, d, f, g, Set("F"))
    }
}

case class Fnext1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, Fnext1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: Fnext1 =
            if (op == "NewPrime") {
                val s = Fnext2Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                NewPrimeFnext(sid, role, actor.deserializeInt(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "Fnext", "F", g)
        Done
    }
}

sealed trait Fnext1 extends Session.IState

case class NewPrimeFnext(sid: Session.Sid, role: Session.Role, x1: Int, s: Fnext2Suspend) extends Fnext1

case class EndFnext(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "Fnext")
        done
    }
}

case class Fnext2Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, Fnext2) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: Fnext2 =
            if (op == "LongBox2") {
                val s = Fnext2Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                LongBox2Fnext(sid, role, actor.deserializeInt(split(0)), s)
            } else     if (op == "Exit2") {
                val s = Fnext3(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                Exit2Fnext(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "Fnext", "F", g)
        Done
    }
}

sealed trait Fnext2 extends Session.IState

case class LongBox2Fnext(sid: Session.Sid, role: Session.Role, x1: Int, s: Fnext2Suspend) extends Fnext2

case class Exit2Fnext(sid: Session.Sid, role: Session.Role, s: Fnext3) extends Fnext2

case class Fnext3(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendAck2(): EndFnext = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "Fnext", "F", "Ack2", pay)
        EndFnext(sid, "Fnext", actor)
    }
}