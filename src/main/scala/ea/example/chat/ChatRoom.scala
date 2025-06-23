package ea.example.chat

import ea.runtime.{Actor, Done, Net, Session}
import ea.example.chat.Chat.{Proto1, Proto2, Proto3}

import java.io.IOException
import java.net.SocketAddress

class DataR extends Session.Data {
    // Move to ChatRoom (not per session data) -- !!! but use this to pair up Proto2/3 sids
    val out: collection.mutable.Map[Session.Sid, Session.LinOption[Proto3.R31]] = collection.mutable.Map()
    val log: collection.mutable.ListBuffer[String] = collection.mutable.ListBuffer()
}

trait Room extends Proto2.ActorR2 with Proto3.ActorR3

class ChatRoom(pid: Net.Pid, port: Net.Port, apPort1: Net.Port, apPort2: Net.Port) extends Actor(pid) with Room {

    val d = new DataR

    def spawn(): Unit = {
        spawn(this.port)
        registerR3(this.port, "localhost", apPort2, this.d, r3_1)
    }

    def r3_1(d: DataR, s: Proto3.R31): Done.type = {
        println(s"r3_1")
        registerR3(this.port, "localhost", apPort2, d, r3_1)
        val (a, done) = Session.freeze(s, (sid: Session.Sid, role: Session.Role, a: Actor) => Proto3.R31(sid, role, a))
        d.out += (s.sid -> a)
        registerR2(this.port, "localhost", apPort1, d, r2_1suspend)
        done
    }

    def r2_1suspend(d: DataR, s: Proto2.R21Suspend): Done.type = {
        println(s"r2_1suspend")
        registerR2(this.port, "localhost", apPort1, d, r2_1suspend)
        s.suspend(d, r2_1)
    }

    def r2_1(d: DataR, s: Proto2.R21): Done.type = {
        s match {
            case Proto2.OutgoingChatMessageR2(sid, role, x, s) =>
                println(s"[$name] received: $x")
                d.log += x
                d.out.keySet.foreach(x =>
                    d.out(x) match {
                        case y: Session.LinSome[_] =>  // Proto3.R31
                            Session.become(d, y, bc)
                        case _: Session.LinNone => throw new RuntimeException("missing frozen")
                    }
                )
                s.suspend(d, r2_1)
            case Proto2.LeaveRoomR2(sid, role, x, s) =>
                s.finish()
        }
    }

    @throws[IOException]
    def bc(d: DataR, s: Proto3.R31): Done.type = {
        val msg = s"${d.log.last}"
        println(s"[$name] sending: $msg")
        val (a, done) = Session.freeze(
            s.sendIncomingChatMessage(msg),
            (sid: Session.Sid, role: Session.Role, a: Actor) => Proto3.R31(sid, role, a))

        d.out += (s.sid -> a)  // !!! overwrite "used"

        done
    }

    override def afterClosed(): Unit = ChatServer.shutdownRooms.add(this.pid);

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
        /*sid.map(x => {
            if (x._1 == "Proto3") {
                println(debugToString(s"Current out cache: ${this.d.out}"))
                println(debugToString(s"GC $x"))
                d.out -= x
            }
        })*/
    }
}
