package ea.example.chat

import ea.runtime.{Actor, Done, Net, Session, Util}
import ea.runtime.Net.{Pid, Port}
import ea.example.chat.Chat.{Proto1, Proto2, Proto3}

import java.net.SocketAddress


object TestChatClient {

    val PORT_C1 = 7777
    val PORT_C2 = 7779

    def main(args: Array[String]): Unit = {
        println("hello")

        val c1 = new ChatClient("client1", PORT_C1)
        //c1.debug = true
        val d1 = c1.spawn()
        c1.run(d1, TestChatServer.PORT_Proto1)

        val c2 = new ChatClient("client2", PORT_C2)
        //c2.debug = true
        val d2 = c2.spawn()
        c2.run(d2, TestChatServer.PORT_Proto1)
    }
}


/* ... */

object ChatClient {

    def main(args: Array[String]): Unit = {
        println("hello")

        val port = if (args.length < 1) TestChatClient.PORT_C1 else args(0).toInt
        val user = if (args.length < 2) "user" else args(1)
        val sAPPort = if (args.length < 3) TestChatServer.PORT_Proto1 else args(2).toInt
        val client = new ChatClient(user, port)
        val d = client.spawn()
        client.run(d, sAPPort)
    }
}

// !!! @targetName manually added inside APIs to resolve ChatProto1/2.registerC clash
trait Client extends Proto1.ActorC with Proto2.ActorC2 with Proto3.ActorC3

class DataC extends Session.Data {
    // Move to ChatClient (not per session data)
    var rPid = "room1"
    var rAPPort1: Int = -1
    var rAPPort2: Int = -1
}

class ChatClient(pid: Net.Pid, port: Net.Port) extends Actor(pid) with Client {

    def spawn(): DataC = {
        val d = new DataC
        spawn(this.port)
        println(s"spawn")
        d
    }

    def run(d: DataC, sAPPort: Net.Port): Unit = {
        // !!! rename 3x registerC in API
        registerC(this.port, "localhost", sAPPort, d, c1)  // ChatProto1.registerC
    }

    def c1(d: DataC, s: Proto1.C1): Done.type = {
        s.sendCreateRoom(d.rPid).suspend(d, c3)
    }

    def c3(d: DataC, s: Proto1.C3): Done.type = {
        val (apPort, done) =
            s match {
                case Proto1.CreateRoomSuccessC(sid, role, x, s) =>
                    println(s"[${name}] create")
                    (x.toInt, s.sendBye("create").finish())
                case Proto1.RoomExistsC(sid, role, x, s) =>
                    println(s"[${name}] exists")
                    (x.toInt, s.sendBye("exists").finish())
            }
        joinRoom(d, d.rPid, apPort)
        done
    }

    def joinRoom(d: DataC, pid: Net.Pid, rAPPort: Net.Port): Unit = {
        // !!! rename 3x registerC in API
        d.rAPPort1 = rAPPort
        d.rAPPort2 = rAPPort + 1
        registerC3(this.port, "localhost", d.rAPPort2, d, c3_1suspend)  // ChatProto3.registerC
    }

    def c3_1suspend(d: DataC, s: Proto3.C31Suspend): Done.type = {
        // !!! rename 3x registerC in API
        registerC2(this.port, "localhost", d.rAPPort1, d, c2_1)  // ChatProto2.registerC
        println(s"c3 suspend")
        s.suspend(d, c3_1)
    }

    def c3_1(d: DataC, s: Proto3.C31): Done.type = {
        s match {
            case Proto3.IncomingChatMessageC3(sid, role, x, s) =>
                println(s"[${name}] received: ${x}")
                s.suspend(d, c3_1)
            case Proto3.ByeC3(sid, role, x, s) =>
                if (!this.out.isUsed) {
                    //finishAndClose(this.out.get)  // !!! cannot end  // normally already ended by app logic (LeaveRoom), but maybe crash
                }
                finishAndClose(s)
        }
    }

    var out: Session.LinOption[Proto2.C21] = Session.LinNone()

    // !!! out stream -- cache/become necessary
    def c2_1(d: DataC, s: Proto2.C21): Done.type = {
        val done = c2_1aux(d, s)
        Util.spawn(() => timer(d))
        done
    }

    def c2_1aux(d: DataC, s: Proto2.C21): Done.type = {
        println(s"[${name}] sending: ${pid}")
        val s1 = s.sendOutgoingChatMessage(s"${pid}")  // !!! reassign
        /*val done = Session.cache(s1,
            (sid: Session.Sid, a: Actor) => proto2.C1(sid, a),
            (a: Some[proto2.C1]) => this.out = a)*/
        val (a, done) = Session.freeze(s, (sid: Session.Sid, role: Session.Role, a: Actor) => Proto2.C21(sid, role, a))
        this.out = a
        done
    }

    // async-become maybe nicer instead of spawn
    def timer(d: DataC): Unit = {
        Thread.sleep(2000)
        this.out match {
            case _: Session.LinNone => ()  // cf. error handling?
            case y: Session.LinSome[Proto2.C21] =>
                Session.become(d, y, c2_1aux)
        }
        timer(d)
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }

}
