package ea.example.lockid.LockId.Proto1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorS extends Actor {

    def registerS[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, S1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => S1Suspend(sid, "S", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto1", "S", port, d, f, g, Set("C"))
    }
}

case class S1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, S1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: S1 =
            if (op == "IDRequest") {
                val s = S2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                IDRequestS(sid, role, actor.deserializeString(split(0)), s)
            } else     if (op == "LockRequest") {
                val s = S3(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                LockRequestS(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "S", "C", g)
        Done
    }
}

sealed trait S1 extends Session.IState

case class IDRequestS(sid: Session.Sid, role: Session.Role, x1: String, s: S2) extends S1

case class LockRequestS(sid: Session.Sid, role: Session.Role, s: S3) extends S1

case class S2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendIDResponse(x1: Int): S1Suspend = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "S", "C", "IDResponse", pay)
        S1Suspend(sid, "S", actor)
    }

    @throws[IOException]
    def sendReqUnavailable(): S1Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "S", "C", "ReqUnavailable", pay)
        S1Suspend(sid, "S", actor)
    }
}

case class S3(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendLocked(): S4Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "S", "C", "Locked", pay)
        S4Suspend(sid, "S", actor)
    }

    @throws[IOException]
    def sendLockUnavailable(): S1Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "S", "C", "LockUnavailable", pay)
        S1Suspend(sid, "S", actor)
    }
}

case class S4Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, S4) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: S4 =
            if (op == "Unlock") {
                val s = S1Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                UnlockS(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "S", "C", g)
        Done
    }
}

sealed trait S4 extends Session.IState

case class UnlockS(sid: Session.Sid, role: Session.Role, s: S1Suspend) extends S4