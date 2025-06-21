package ea.example.shoprestock.Shop.Proto2

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorSS extends Actor {

    def registerSS[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, SS1) => Done.type): Unit = {
        val g = (sid: Session.Sid) => SS1(sid, "SS", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto2", "SS", port, d, f, g, Set("SF"))
    }
}

case class SS1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendAddItem(x1: String): SS1 = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "SS", "SF", "AddItem", pay)
        SS1(sid, "SS", actor)
    }

    @throws[IOException]
    def sendRemoveItem(x1: String): SS1 = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "SS", "SF", "RemoveItem", pay)
        SS1(sid, "SS", actor)
    }
}