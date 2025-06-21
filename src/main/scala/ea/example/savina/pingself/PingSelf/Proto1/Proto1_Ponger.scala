package ea.example.savina.pingself.PingSelf.Proto1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorPonger extends Actor {

    def registerPonger[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, Ponger1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => Ponger1Suspend(sid, "Ponger", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto1", "Ponger", port, d, f, g, Set("Pinger"))
    }
}

case class Ponger1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, Ponger1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: Ponger1 =
            if (op == "Ping0") {
                val s = Ponger2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                Ping0Ponger(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "Ponger", "Pinger", g)
        Done
    }
}

sealed trait Ponger1 extends Session.IState

case class Ping0Ponger(sid: Session.Sid, role: Session.Role, s: Ponger2) extends Ponger1

case class EndPonger(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "Ponger")
        done
    }
}

case class Ponger2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendPong0(): Ponger3Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "Ponger", "Pinger", "Pong0", pay)
        Ponger3Suspend(sid, "Ponger", actor)
    }
}

case class Ponger3Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, Ponger3) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: Ponger3 =
            if (op == "Ping") {
                val s = Ponger4(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                PingPonger(sid, role, s)
            } else     if (op == "Stop") {
                val s = EndPonger(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                StopPonger(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "Ponger", "Pinger", g)
        Done
    }
}

sealed trait Ponger3 extends Session.IState

case class PingPonger(sid: Session.Sid, role: Session.Role, s: Ponger4) extends Ponger3

case class StopPonger(sid: Session.Sid, role: Session.Role, s: EndPonger) extends Ponger3

case class Ponger4(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendPong(): Ponger3Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "Ponger", "Pinger", "Pong", pay)
        Ponger3Suspend(sid, "Ponger", actor)
    }
}