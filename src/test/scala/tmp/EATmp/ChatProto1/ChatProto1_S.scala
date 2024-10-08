package tmp.EATmp.ChatProto1

import ea.runtime.{Actor, Done, Session}

import java.io.IOException

trait ActorS extends Actor {

    def registerS[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, S1Suspend) => Done.type): Unit = {
        val g = (sid: Session.Sid) => S1Suspend(sid, "S", this)
        enqueueRegisterForPeers(apHost, apPort, "ChatProto1", "S", port, d, f, g, Set("C"))
    }
}

case class S1Suspend(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.SuspendState[Actor] {

    def suspend[D <: Session.Data](d: D, f: (D, S1) => Done.type): Done.type = {
        checkNotUsed()
        val g = (op: String, pay: String) => {
            var succ: Option[Session.ActorState[Actor]] = None
            val msg: S1 =
            if (op == "LookupRoom") {
                val s = S2(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                LookupRoomS(sid, role, actor.deserializeString(split(0)), s)
            } else     if (op == "CreateRoom") {
                val s = S3(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                CreateRoomS(sid, role, actor.deserializeString(split(0)), s)
            } else     if (op == "ListRooms") {
                val s = S4(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                ListRoomsS(sid, role, actor.deserializeString(split(0)), s)
            } else     if (op == "Bye") {
                val s = EndS(sid, role, actor)
                succ = Some(s)
                val split = pay.split("::::")
                ByeS(sid, role, actor.deserializeString(split(0)), s)
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

case class LookupRoomS(sid: Session.Sid, role: Session.Role, x1: String, s: S2) extends S1

case class CreateRoomS(sid: Session.Sid, role: Session.Role, x1: String, s: S3) extends S1

case class ListRoomsS(sid: Session.Sid, role: Session.Role, x1: String, s: S4) extends S1

case class ByeS(sid: Session.Sid, role: Session.Role, x1: String, s: EndS) extends S1

case class EndS(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.End[Actor] {

    override def finish(): Done.type = {
        checkNotUsed()
        val done = super.finish()
        actor.end(sid, "S")
        done
    }
}

case class S2(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendRoomPID(x1: String): S1Suspend = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "S", "C", "RoomPID", pay)
        S1Suspend(sid, "S", actor)
    }

    @throws[IOException]
    def sendRoomNotFound(x1: String): S1Suspend = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "S", "C", "RoomNotFound", pay)
        S1Suspend(sid, "S", actor)
    }
}

case class S3(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendCreateRoomSuccess(x1: String): S1Suspend = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "S", "C", "CreateRoomSuccess", pay)
        S1Suspend(sid, "S", actor)
    }

    @throws[IOException]
    def sendRoomExists(x1: String): S1Suspend = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "S", "C", "RoomExists", pay)
        S1Suspend(sid, "S", actor)
    }
}

case class S4(sid: Session.Sid, role: Session.Role, actor: Actor) extends Session.OState[Actor] {

    @throws[IOException]
    def sendRoomList(x1: String): S1Suspend = {
        checkNotUsed()
        val pay = actor.serializeString(x1)
        actor.sendMessage(sid, "S", "C", "RoomList", pay)
        S1Suspend(sid, "S", actor)
    }
}