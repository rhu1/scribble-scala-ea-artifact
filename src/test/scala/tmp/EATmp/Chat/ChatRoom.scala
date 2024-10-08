package tmp.EATmp.Chat

import ea.runtime.{Actor, Done, Net, Session}
import tmp.EATmp.{ChatProto2, ChatProto3}

import java.io.IOException
import java.net.SocketAddress

object TestChatRoom {

    def main(args: Array[String]): Unit = {
        println("hello")

        val ap2 = new ChatProto2.ChatProto2
        val ap3 = new ChatProto3.ChatProto3
        //proto3.debug = true
        ap2.spawn(9998)
        ap3.spawn(9999)

        val room1 = new ChatRoom("room1", 8888, 9998)
        //room1.debug = true
        room1.spawn()
    }

}

class DataR extends Session.Data {
    // Move to ChatRoom (not per session data) -- !!! but use this to pair up Proto2/3 sids
    val out: collection.mutable.Map[Session.Sid, Session.LinOption[ChatProto3.R31]] = collection.mutable.Map()
    val log: collection.mutable.ListBuffer[String] = collection.mutable.ListBuffer()
}

// !!! @targetName manually added inside APIs to resolve ChatProto2/3.registerR clash
trait Room extends ChatProto2.ActorR2 with ChatProto3.ActorR3

class ChatRoom(pid: Net.Pid, port: Net.Port, apPort: Net.Port) extends Actor(pid) with Room {

    val d = new DataR  // !!! thread through EventServer to keep local (so can provide to handleException)

    def spawn(): Unit = {
        //val d = new DataR
        spawn(this.port)
        // !!! rename 2x registerR in API
        registerR3(this.port, "localhost", apPort+1, this.d, r3_1)  // ChatProto3.registerR
    }

    def r3_1(d: DataR, s: ChatProto3.R31): Done.type = {
        println(s"r3_1")
        registerR3(this.port, "localhost", apPort+1, d, r3_1)  // ChatProto3.registerR
        /*val done = Session.cache(s,
            (sid: Session.Sid, a: Actor) => proto3.R1(sid, a),
            (a: Some[proto3.R1]) => d.out += (a.get.sid -> a))  // !!! get*/
        val (a, done) = Session.freeze(s, (sid: Session.Sid, role: Session.Role, a: Actor) => ChatProto3.R31(sid, role, a))
        d.out += (s.sid -> a)  // !!! overwrite "used"
        // !!! rename 2x registerR in API
        registerR2(this.port, "localhost", apPort, d, r2_1suspend)  // ChatProto2.regsiterR
        done
    }

    def r2_1suspend(d: DataR, s: ChatProto2.R21Suspend): Done.type = {
        println(s"r2_1suspend")
        registerR2(this.port, "localhost", apPort, d, r2_1suspend)  // ChatProto2.registerR
        s.suspend(d, r2_1)
    }

    def r2_1(d: DataR, s: ChatProto2.R21): Done.type = {
        s match {
            case ChatProto2.OutgoingChatMessageR2(sid, role, x, s) =>
                println(s"[$name] received: $x")
                d.log += x
                d.out.keySet.foreach(x =>  // toList for copy? or keySet already a copy?
                    d.out(x) match {
                        case _: Session.LinNone => ()
                        case y: Session.LinSome[ChatProto3.R31] =>
                            // !!! XXX cannot match on get because linear
                            Session.become(d, y, bc)
                    }
                )
                s.suspend(d, r2_1)
            case ChatProto2.LeaveRoomR2(sid, role, x, s) =>
                //d.out -= sid  // !!! send BYE  // XXX wrong sid  // TODO use d to pair up sids
                //finishAndClose(s)
                s.finish()
        }
    }

    @throws[IOException]
    def bc(d: DataR, s: ChatProto3.R31): Done.type = {
        val msg = s"${d.log.last}"
        println(s"[$name] sending: $msg")
        /*Session.cache(s.sendIncomingChatMessage(d.log.last),
            (sid: Session.Sid, a: Actor) => proto3.R1(sid, a),
            (a: Some[proto3.R1]) => d.out += (a.get.sid -> a))  // !!! get*/
        val (a, done) = Session.freeze(
            s.sendIncomingChatMessage(msg),
            (sid: Session.Sid, role: Session.Role, a: Actor) => ChatProto3.R31(sid, role, a))  // copy constructor (consume linear)

        // CHECKME concurrent modif with d.out.foreach?
        d.out += (s.sid -> a)  // !!! overwrite "used"

        done
    }

    /*trait I {
        type inner
        val y: inner
    }

    def foo(x: I): x.inner = {
        ....
    }*/

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
        sid.map(x => {
            if (x._1 == "ChatProto3") {
                println(debugToString(s"Current out cache: ${this.d.out}"))
                println(debugToString(s"GC $x"))
                d.out -= x
            }
        })
    }
}
