package tmp.EATmp.PingPong2

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorPinger extends Actor {

    def registerPinger[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, Pinger1) => Done.type): Unit = {
        val g = (sid: Session.Sid) => Pinger1(sid, "Pinger", this)
        enqueueRegisterForPeers(apHost, apPort, "PingPong2", "Pinger", port, d, f, g, Set("Ponger"))
    }
}

case class Pinger1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendPing0(): Pinger2Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "Pinger", "Ponger", "Ping0", pay)
        Pinger2Suspend(sid, "Pinger", actor)
    }
}

case class EndPinger(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "Pinger")
        done
    }
}

case class Pinger2Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, Pinger2) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: Pinger2 =
            if (op == "Pong0") {
                val s = Pinger3(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                Pong0Pinger(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "Pinger", "Ponger", g)
        Done
    }
}

sealed trait Pinger2 extends Session.IState

case class Pong0Pinger(sid: Session.Sid, role: Session.Role, s: Pinger3) extends Pinger2

case class Pinger3(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendPing(): Pinger4Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "Pinger", "Ponger", "Ping", pay)
        Pinger4Suspend(sid, "Pinger", actor)
    }

    @throws[IOException]
    def sendStop(): EndPinger = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "Pinger", "Ponger", "Stop", pay)
        EndPinger(sid, "Pinger", actor)
    }
}

case class Pinger4Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, Pinger4) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: Pinger4 =
            if (op == "Pong") {
                val s = Pinger3(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                PongPinger(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "Pinger", "Ponger", g)
        Done
    }
}

sealed trait Pinger4 extends Session.IState

case class PongPinger(sid: Session.Sid, role: Session.Role, s: Pinger3) extends Pinger4