package ea.example.savina.diningself.DiningSelf.Proto2

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorP extends Actor {

    def registerP[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, P1) => Done.type): Unit = {
        val g = (sid: Session.Sid) => P1(sid, "P", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto2", "P", port, d, f, g, Set("A"))
    }
}

case class P1(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendHungry0(x1: Int): P2Suspend = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "P", "A", "Hungry0", pay)
        P2Suspend(sid, "P", actor)
    }
}

case class EndP(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "P")
        done
    }
}

case class P2Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, P2) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: P2 =
            if (op == "Denied") {
                val s = P3(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                DeniedP(sid, role, s)
            } else     if (op == "Eat") {
                val s = P4(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                EatP(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "P", "A", g)
        Done
    }
}

sealed trait P2 extends Session.IState

case class DeniedP(sid: Session.Sid, role: Session.Role, s: P3) extends P2

case class EatP(sid: Session.Sid, role: Session.Role, s: P4) extends P2

case class P3(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendHungryD(x1: Int): P2Suspend = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "P", "A", "HungryD", pay)
        P2Suspend(sid, "P", actor)
    }
}

case class P4(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendDone(x1: Int): P5 = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "P", "A", "Done", pay)
        P5(sid, "P", actor)
    }
}

case class P5(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendHungryE(x1: Int): P2Suspend = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "P", "A", "HungryE", pay)
        P2Suspend(sid, "P", actor)
    }

    @throws[IOException]
    def sendExit(): EndP = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "P", "A", "Exit", pay)
        EndP(sid, "P", actor)
    }
}