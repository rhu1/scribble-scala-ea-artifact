package ea.example.savina.ping.Ping.Proto1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorPongReceiver extends Actor {

    def registerPongReceiver[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, PongReceiver1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => PongReceiver1Suspend(sid, "PongReceiver", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto1", "PongReceiver", port, d, f, g, Set("C", "Pinger", "Ponger"))
    }
}

case class PongReceiver1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, PongReceiver1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: PongReceiver1 =
            if (op == "Pong0") {
                val s = PongReceiver2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                Pong0PongReceiver(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "PongReceiver", "Ponger", g)
        Done
    }
}

sealed trait PongReceiver1 extends Session.IState

case class Pong0PongReceiver(sid: Session.Sid, role: Session.Role, s: PongReceiver2) extends PongReceiver1

case class EndPongReceiver(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "PongReceiver")
        done
    }
}

case class PongReceiver2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendPingC(): PongReceiver3Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "PongReceiver", "Pinger", "PingC", pay)
        PongReceiver3Suspend(sid, "PongReceiver", actor)
    }

    @throws[IOException]
    def sendStop(): EndPongReceiver = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "PongReceiver", "Pinger", "Stop", pay)
        EndPongReceiver(sid, "PongReceiver", actor)
    }
}

case class PongReceiver3Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, PongReceiver3) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: PongReceiver3 =
            if (op == "Pong") {
                val s = PongReceiver2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                PongPongReceiver(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "PongReceiver", "Ponger", g)
        Done
    }
}

sealed trait PongReceiver3 extends Session.IState

case class PongPongReceiver(sid: Session.Sid, role: Session.Role, s: PongReceiver2) extends PongReceiver3