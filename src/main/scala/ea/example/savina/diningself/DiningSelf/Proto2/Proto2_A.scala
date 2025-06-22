package ea.example.savina.diningself.DiningSelf.Proto2

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorA extends Actor {

    def registerA[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, A1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => A1Suspend(sid, "A", this)
        enqueueRegisterForPeers(apHost, apPort, "Proto2", "A", port, d, f, g, Set("P"))
    }
}

case class A1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, A1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: A1 =
            if (op == "Hungry0") {
                val s = A2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                Hungry0A(sid, role, actor.deserializeInt(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "A", "P", g)
        Done
    }
}

sealed trait A1 extends Session.IState

case class Hungry0A(sid: Session.Sid, role: Session.Role, x1: Int, s: A2) extends A1

case class EndA(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "A")
        done
    }
}

case class A2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendDenied(): A3Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "A", "P", "Denied", pay)
        A3Suspend(sid, "A", actor)
    }

    @throws[IOException]
    def sendEat(): A4Suspend = {
        checkNotUsed()
        val pay = ""
        actor.sendMessage(sid, "A", "P", "Eat", pay)
        A4Suspend(sid, "A", actor)
    }
}

case class A3Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, A3) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: A3 =
            if (op == "HungryD") {
                val s = A2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                HungryDA(sid, role, actor.deserializeInt(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "A", "P", g)
        Done
    }
}

sealed trait A3 extends Session.IState

case class HungryDA(sid: Session.Sid, role: Session.Role, x1: Int, s: A2) extends A3

case class A4Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, A4) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: A4 =
            if (op == "Done") {
                val s = A5Suspend(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                DoneA(sid, role, actor.deserializeInt(split(0)), s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "A", "P", g)
        Done
    }
}

sealed trait A4 extends Session.IState

case class DoneA(sid: Session.Sid, role: Session.Role, x1: Int, s: A5Suspend) extends A4

case class A5Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, A5) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: A5 =
            if (op == "HungryE") {
                val s = A2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                HungryEA(sid, role, actor.deserializeInt(split(0)), s)
            } else     if (op == "Exit") {
                val s = EndA(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                ExitA(sid, role, s)
            } else {
                throw new RuntimeException(s"[ERROR] Unexpected op: ${op}(${pay})")
            }
            val done = f.apply(d, msg)
            succ.get.checkUsed()
            done
        }
        actor.setHandler(sid, "A", "P", g)
        Done
    }
}

sealed trait A5 extends Session.IState

case class HungryEA(sid: Session.Sid, role: Session.Role, x1: Int, s: A2) extends A5

case class ExitA(sid: Session.Sid, role: Session.Role, s: EndA) extends A5