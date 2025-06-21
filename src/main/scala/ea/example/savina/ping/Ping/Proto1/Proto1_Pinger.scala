package ea.example.savina.ping.Ping.Proto1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorPinger extends Actor {

    def registerPinger[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, Pinger1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => Pinger1Suspend(sid, "Pinger", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto1", "Pinger", port, d, f, g, Set("C", "Ponger", "PongReceiver"))
    }
}

case class Pinger1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, Pinger1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: Pinger1 =
            if (op == "Start") {
                val s = Pinger2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                StartPinger(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "Pinger", "C", g)
        Done
    }
}

sealed trait Pinger1 extends Session.IState

case class StartPinger(sid: Session.Sid, role: Session.Role, s: Pinger2) extends Pinger1

case class EndPinger(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "Pinger")
        done
    }
}

case class Pinger2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendPing0(): Pinger3Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "Pinger", "Ponger", "Ping0", pay)
        Pinger3Suspend(sid, "Pinger", actor)
    }
}

case class Pinger3Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, Pinger3) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: Pinger3 =
            if (op == "PingC") {
                val s = Pinger4(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                PingCPinger(sid, role, s)
            } else     if (op == "Stop") {
                val s = Pinger5(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                StopPinger(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "Pinger", "PongReceiver", g)
        Done
    }
}

sealed trait Pinger3 extends Session.IState

case class PingCPinger(sid: Session.Sid, role: Session.Role, s: Pinger4) extends Pinger3

case class StopPinger(sid: Session.Sid, role: Session.Role, s: Pinger5) extends Pinger3

case class Pinger4(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendPing(): Pinger3Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "Pinger", "Ponger", "Ping", pay)
        Pinger3Suspend(sid, "Pinger", actor)
    }
}

case class Pinger5(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendStop(): Pinger6 = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "Pinger", "Ponger", "Stop", pay)
        Pinger6(sid, "Pinger", actor)
    }
}

case class Pinger6(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendStop(): EndPinger = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "Pinger", "C", "Stop", pay)
        EndPinger(sid, "Pinger", actor)
    }
}