package tmp.EATmp.Sieve1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorG extends Actor {

    def registerG[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, G1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => G1Suspend(sid, "G", this)
        enqueueRegisterForPeers(apHost, apPort, "Sieve1", "G", port, d, f, g, Set("M", "F1"))
    }
}

case class G1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, G1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: G1 =
            if (op == "Start") {
                val s = G2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                StartG(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "G", "M", g)
        Done
    }
}

sealed trait G1 extends Session.IState

case class StartG(sid: Session.Sid, role: Session.Role, s: G2) extends G1

case class EndG(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "G")
        done
    }
}

case class G2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendNewPrime(x1: Int): G3 = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "G", "F1", "NewPrime", pay)
        G3(sid, "G", actor)
    }
}

case class G3(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendLongBox(x1: Int): G3 = {
        checkNotUsed()
        val pay = actor.serializeInt(x1)
        actor.sendMessage(sid, "G", "F1", "LongBox", pay)
        G3(sid, "G", actor)
    }

    @throws[IOException]
    def sendExit(): G4Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "G", "F1", "Exit", pay)
        G4Suspend(sid, "G", actor)
    }
}

case class G4Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, G4) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: G4 =
            if (op == "Ack") {
                val s = G5(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                AckG(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "G", "F1", g)
        Done
    }
}

sealed trait G4 extends Session.IState

case class AckG(sid: Session.Sid, role: Session.Role, s: G5) extends G4

case class G5(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendExit(): EndG = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "G", "M", "Exit", pay)
        EndG(sid, "G", actor)
    }
}