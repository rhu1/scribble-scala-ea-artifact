package ea.example.shoprestock.Shop.Proto2

import ea.runtime.{Actor, Done, Session}

trait ActorSF extends Actor {

    def registerSF[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, SF1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => SF1Suspend(sid, "SF", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto2", "SF", port, d, f, g, Set("SS"))
    }
}

case class SF1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, SF1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: SF1 =
            if (op == "AddItem") {
                val s = SF1Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                AddItemSF(sid, role, actor.deserializeString(split(0)), s)
            } else     if (op == "RemoveItem") {
                val s = SF1Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                RemoveItemSF(sid, role, actor.deserializeString(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "SF", "SS", g)
        Done
    }
}

sealed trait SF1 extends Session.IState

case class AddItemSF(sid: Session.Sid, role: Session.Role, x1: String, s: SF1Suspend) extends SF1

case class RemoveItemSF(sid: Session.Sid, role: Session.Role, x1: String, s: SF1Suspend) extends SF1