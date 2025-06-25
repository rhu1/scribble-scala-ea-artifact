package ea.example.chat

import ea.example.chat.Chat.{Proto1, Proto2, Proto3}
import ea.runtime.Net.{Pid, Port}
import ea.runtime.{Actor, Done, Session, Util}

import java.net.SocketAddress


object ChatClient {

    def main(args: Array[String]): Unit = {
        val port_C = if (args.length < 1) TestChatServer.PORT_C1 else args(0).toInt
        val user = if (args.length < 2) "user" else args(1)
        val port_Proto1 = if (args.length < 3) TestChatServer.PORT_Proto1 else args(2).toInt
        val client = new ChatClient(user, port_C)
        val d = client.spawn()
        client.run(d, port_Proto1)
    }
}

trait Client extends Proto1.ActorC with Proto2.ActorC2 with Proto3.ActorC3 {}

class Data_C extends Session.Data {
    var pid_Room = "room1"
    var port_Proto2: Int = -1
    var port_Proto3: Int = -1
    var out: Session.LinOption[Proto2.C21] = Session.LinNone()
}

class ChatClient(pid_C: Pid, port_C: Port) extends Actor(pid_C) with Client {

    def spawn(): Data_C =
        val d = new Data_C
        spawn(this.port_C)
        d

    def run(d: Data_C, sAPPort: Port): Unit =
        registerC(this.port_C, "localhost", sAPPort, d, c1)

    /* Proto1 */

    def c1(d: Data_C, s: Proto1.C1): Done.type =
        s.sendCreateRoom(d.pid_Room).suspend(d, c3)

    def c3(d: Data_C, s: Proto1.C3): Done.type =
        val (apPort, done) = s match {
            case Proto1.CreateRoomSuccessC(sid, role, x, s) =>
                println(s"[${name}] create ${d.pid_Room} @ $x")
                (x.toInt, s.sendBye("create").finish())
            case Proto1.RoomExistsC(sid, role, x, s) =>
                println(s"[${name}] exists ${d.pid_Room} @ $x")
                (x.toInt, s.sendBye("exists").finish())
        }
        joinRoom(d, d.pid_Room, apPort)
        done

    def joinRoom(d: Data_C, pid: Pid, port_Proto2: Port): Unit =
        d.port_Proto2 = port_Proto2
        d.port_Proto3 = port_Proto2 + 1  // Implicit...
        registerC3(this.port_C, "localhost", d.port_Proto3, d, c3_1suspend)

    /* Proto3 */

    def c3_1suspend(d: Data_C, s: Proto3.C31Suspend): Done.type =
        registerC2(this.port_C, "localhost", d.port_Proto2, d, c2_1)
        s.suspend(d, c3_1)

    def c3_1(d: Data_C, s: Proto3.C31): Done.type = s match {
        case Proto3.IncomingChatMessageC3(sid, role, x, s) =>
            println(s"[${name}] received: ${x}")
            s.suspend(d, c3_1)
        case Proto3.ByeC3(sid, role, x, s) =>
            if (!d.out.isUsed) {
                d.out.get.sendLeaveRoom("Bye from $pid").finish()
            }
            finishAndClose(s)
    }

    /* Proto2 */

    def c2_1(d: Data_C, s: Proto2.C21): Done.type =
        val done = c2_1aux(d, s)
        Util.spawn(() => timer(d))
        done

    def c2_1aux(d: Data_C, s: Proto2.C21): Done.type =
        println(s"[${name}] sending... ${pid_C}")
        val s1 = s.sendOutgoingChatMessage(s"${pid_C}")
        val (a, done) = Session.freeze(s,
            (sid: Session.Sid, role: Session.Role, a: Actor) => Proto2.C21(sid, role, a))
        d.out = a
        done

    private var runThread = true

    def timer(d: Data_C): Unit =
        while (this.runThread) {
            Thread.sleep(2000)
            d.out match {
                case y: Session.LinSome[_] => Session.become(d, y, c2_1aux)
                case _: Session.LinNone => throw new RuntimeException("Missing frozen...")
            }
        }

    /* Close */

    override def afterClosed(): Unit =
        this.runThread = false
        TestChatServer.shutdown.add(this.pid_C)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()

}
