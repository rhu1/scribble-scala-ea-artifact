package ea.example.shoprestock.Shop.Proto1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorP extends Actor {

    def registerP[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, P1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => P1Suspend(sid, "P", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto1", "P", port, d, f, g, Set("C", "S"))
    }
}

case class P1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, P1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: P1 =
            if (op == "Buy") {
                val s = P2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                BuyP(sid, role, actor.deserializeString(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "P", "S", g)
        Done
    }
}

sealed trait P1 extends Session.IState

case class BuyP(sid: Session.Sid, role: Session.Role, x1: String, s: P2) extends P1

case class P2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendOK(x1: String): P1Suspend = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "P", "S", "OK", pay)
        P1Suspend(sid, "P", actor)
    }

    @throws[IOException]
    def sendDeclined(x1: String): P1Suspend = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "P", "S", "Declined", pay)
        P1Suspend(sid, "P", actor)
    }
}