package ea.example.robot.Robot.Proto1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorW extends Actor {

    def registerW[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, W1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => W1Suspend(sid, "W", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto1", "W", port, d, f, g, Set("R", "D"))
    }
}

case class W1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, W1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: W1 =
            if (op == "WantW") {
                val s = W2Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                WantWW(sid, role, actor.deserializeString(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "W", "R", g)
        Done
    }
}

sealed trait W1 extends Session.IState

case class WantWW(sid: Session.Sid, role: Session.Role, x1: String, s: W2Suspend) extends W1

case class EndW(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "W")
        done
    }
}

case class W2Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, W2) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: W2 =
            if (op == "Cancel") {
                val s = EndW(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                CancelW(sid, role, actor.deserializeString(split(0)), s)
            } else     if (op == "Prepare") {
                val s = W3(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                PrepareW(sid, role, actor.deserializeString(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "W", "D", g)
        Done
    }
}

sealed trait W2 extends Session.IState

case class CancelW(sid: Session.Sid, role: Session.Role, x1: String, s: EndW) extends W2

case class PrepareW(sid: Session.Sid, role: Session.Role, x1: String, s: W3) extends W2

case class W3(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendPrepared(x1: String): W4Suspend = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "W", "D", "Prepared", pay)
        W4Suspend(sid, "W", actor)
    }
}

case class W4Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, W4) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: W4 =
            if (op == "Deliver") {
                val s = W5(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                DeliverW(sid, role, actor.deserializeString(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "W", "D", g)
        Done
    }
}

sealed trait W4 extends Session.IState

case class DeliverW(sid: Session.Sid, role: Session.Role, x1: String, s: W5) extends W4

case class W5(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendDelivered(x1: String): W6Suspend = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "W", "R", "Delivered", pay)
        W6Suspend(sid, "W", actor)
    }
}

case class W6Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, W6) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: W6 =
            if (op == "PartTaken") {
                val s = W7(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                PartTakenW(sid, role, actor.deserializeString(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "W", "R", g)
        Done
    }
}

sealed trait W6 extends Session.IState

case class PartTakenW(sid: Session.Sid, role: Session.Role, x1: String, s: W7) extends W6

case class W7(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendTableIdle(x1: String): EndW = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "W", "D", "TableIdle", pay)
        EndW(sid, "W", actor)
    }
}