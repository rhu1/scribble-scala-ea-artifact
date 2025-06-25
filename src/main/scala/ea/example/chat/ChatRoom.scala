package ea.example.chat

import ea.example.chat.Chat.{Proto2, Proto3}
import ea.runtime.Net.{Pid_C, Port}
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress

class Data_R extends Session.Data {}

trait Room extends Proto2.ActorR2 with Proto3.ActorR3

class ChatRoom(pid_R: Pid_C, port_R: Port, port_Proto2: Port, port_Proto3: Port)
    extends Actor(pid_R) with Room {

    private val log: collection.mutable.ListBuffer[String] = collection.mutable.ListBuffer()
    private val out: collection.mutable.Map[Session.Sid, Session.LinOption[Proto3.R31]] =
        collection.mutable.Map()

    def spawn(): Unit =
        spawn(this.port_R)
        registerR3(this.port_R, "localhost", port_Proto3, new Data_R, r3_1)

    /* Proto3 */

    def r3_1(d: Data_R, s: Proto3.R31): Done.type =
        registerR3(this.port_R, "localhost", port_Proto3, d, r3_1)
        val (a, done) = Session.freeze(s,
            (sid: Session.Sid, role: Session.Role, a: Actor) => Proto3.R31(sid, role, a))
        this.out += (s.sid -> a)
        registerR2(this.port_R, "localhost", port_Proto2, d, r2_1suspend)
        done

    /* Proto2 */

    def r2_1suspend(d: Data_R, s: Proto2.R21Suspend): Done.type =
        registerR2(this.port_R, "localhost", port_Proto2, d, r2_1suspend)
        s.suspend(d, r2_1)

    def r2_1(d: Data_R, s: Proto2.R21): Done.type = s match {
        case Proto2.OutgoingChatMessageR2(sid, role, x, s) =>
            println(s"[$name] Received: $x")
            this.log += x
            this.out.keySet.foreach(x =>
                this.out(x) match {
                    case y: Session.LinSome[_] => Session.become(d, y, send)  // Proto3.R31
                    case _: Session.LinNone => throw new RuntimeException("Missing frozen...")
                })
            s.suspend(d, r2_1)
        case Proto2.LeaveRoomR2(sid, role, x, s) => s.finish()
    }

    private def send(d: Data_R, s: Proto3.R31): Done.type =
        val msg = s"${this.log.last}"
        println(s"[$name] Sending... $msg")
        val (a, done) = Session.freeze(
            s.sendIncomingChatMessage(msg),
            (sid: Session.Sid, role: Session.Role, a: Actor) => Proto3.R31(sid, role, a))
        this.out += (s.sid -> a)
        done

    /* Close */

    override def afterClosed(): Unit = ChatServer.shutdownRooms.add(this.pid_R)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}
