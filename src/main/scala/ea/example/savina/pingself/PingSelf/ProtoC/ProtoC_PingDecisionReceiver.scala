package ea.example.savina.pingself.PingSelf.ProtoC

import ea.runtime.{Actor, Done, Session}

trait ActorPingDecisionReceiver extends Actor {

    def registerPingDecisionReceiver[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, PingDecisionReceiver1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => PingDecisionReceiver1Suspend(sid, "PingDecisionReceiver", this)
        enqueueRegisterForPeers(apHost, apPort, "ProtoC", "PingDecisionReceiver", port, d, f, g, Set("C", "PingDecisionMaker"))
    }
}

case class PingDecisionReceiver1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, PingDecisionReceiver1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: PingDecisionReceiver1 =
            if (op == "Start") {
                val s = PingDecisionReceiver2Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                StartPingDecisionReceiver(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "PingDecisionReceiver", "C", g)
        Done
    }
}

sealed trait PingDecisionReceiver1 extends Session.IState

case class StartPingDecisionReceiver(sid: Session.Sid, role: Session.Role, s: PingDecisionReceiver2Suspend) extends PingDecisionReceiver1

case class EndPingDecisionReceiver(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "PingDecisionReceiver")
        done
    }
}

case class PingDecisionReceiver2Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, PingDecisionReceiver2) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: PingDecisionReceiver2 =
            if (op == "Ping") {
                val s = PingDecisionReceiver2Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                PingPingDecisionReceiver(sid, role, s)
            } else     if (op == "StopC") {
                val s = EndPingDecisionReceiver(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                StopCPingDecisionReceiver(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "PingDecisionReceiver", "PingDecisionMaker", g)
        Done
    }
}

sealed trait PingDecisionReceiver2 extends Session.IState

case class PingPingDecisionReceiver(sid: Session.Sid, role: Session.Role, s: PingDecisionReceiver2Suspend) extends PingDecisionReceiver2

case class StopCPingDecisionReceiver(sid: Session.Sid, role: Session.Role, s: EndPingDecisionReceiver) extends PingDecisionReceiver2