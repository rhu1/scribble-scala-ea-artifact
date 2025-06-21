package ea.example.lockid.LockId.Proto1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorC extends Actor {

    def registerC[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, C1) => Done.type): Unit = {
        val g = (sid: Session.Sid) => C1(sid, "C", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto1", "C", port, d, f, g, Set("S"))
    }
}

case class C1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendIDRequest(x1: String): C2Suspend = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "C", "S", "IDRequest", pay)
        C2Suspend(sid, "C", actor)
    }

    @throws[IOException]
    def sendLockRequest(): C3Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "C", "S", "LockRequest", pay)
        C3Suspend(sid, "C", actor)
    }
}

case class C2Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, C2) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: C2 =
            if (op == "IDResponse") {
                val s = C1(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                IDResponseC(sid, role, actor.deserializeInt(split(0)), s)
            } else     if (op == "ReqUnavailable") {
                val s = C1(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                ReqUnavailableC(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "C", "S", g)
        Done
    }
}

sealed trait C2 extends Session.IState

case class IDResponseC(sid: Session.Sid, role: Session.Role, x1: Int, s: C1) extends C2

case class ReqUnavailableC(sid: Session.Sid, role: Session.Role, s: C1) extends C2

case class C3Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, C3) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: C3 =
            if (op == "Locked") {
                val s = C4(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                LockedC(sid, role, s)
            } else     if (op == "LockUnavailable") {
                val s = C1(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                LockUnavailableC(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "C", "S", g)
        Done
    }
}

sealed trait C3 extends Session.IState

case class LockedC(sid: Session.Sid, role: Session.Role, s: C4) extends C3

case class LockUnavailableC(sid: Session.Sid, role: Session.Role, s: C1) extends C3

case class C4(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendUnlock(): C1 = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "C", "S", "Unlock", pay)
        C1(sid, "C", actor)
    }
}