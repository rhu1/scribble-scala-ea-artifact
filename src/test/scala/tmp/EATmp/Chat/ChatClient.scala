package tmp.EATmp.Chat

import ea.runtime.{Actor, Done, Net, Session, Util}
import ea.runtime.Net.{Pid, Port}
import tmp.EATmp.{ChatProto1, ChatProto2, ChatProto3}

object TestChatRoomClient {

    def main(args: Array[String]): Unit = {
        println("hello")

        val client1 = new ChatClient("client1", 7777)
        //client1.debug = true
        val d1 = client1.spawn()
        client1.joinRoom(d1, "room1", 9998)

        val client2 = new ChatClient("client2", 6666)
        val d2 = client2.spawn()
        client2.joinRoom(d2, "room1", 9998)
    }

}

object TestChatClient {

    def main(args: Array[String]): Unit = {
        println("hello")

        val c1 = new ChatClient("client1", 7777)
        //c1.debug = true
        val d1 = c1.spawn()
        c1.run(d1, 9997)

        val c2 = new ChatClient("client2", 7779)
        //c2.debug = true
        val d2 = c2.spawn()
        c2.run(d2, 9997)
    }
}


/* ... */

object ChatClient {

    def main(args: Array[String]): Unit = {
        println("hello")

        val port = if (args.length < 1) 7777 else args(0).toInt
        val user = if (args.length < 2) "user" else args(1)
        val sAPPort = if (args.length < 3) 9997 else args(2).toInt
        val client = new ChatClient(user, port)
        val d = client.spawn()
        client.run(d, sAPPort)
    }
}

trait Client extends ChatProto1.ActorC with ChatProto2.ActorC with ChatProto3.ActorC {

    // !!! @targetName manually added inside APIs to resolve ChatProto1/2.registerC clash

    def registerC3[D <: Session.Data](port: Int, apHost: String, apPort: Int, d: D, f: (D, ChatProto3.C1Suspend) => Done.type): Unit = {
        registerC(port, apHost, apPort, d, f)
    }
}

class DataC extends Session.Data {
    // Move to ChatClient (not per session data)
    var rPid = "room1"
    var rAPPort: Int = -1
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

    def c1(d: DataC, s: ChatProto1.C1): Done.type = {
        s.sendCreateRoom(d.rPid).suspend(d, c3)
    }

    def c3(d: DataC, s: ChatProto1.C3): Done.type = {
        val (apPort, done) =
            s match {
                case ChatProto1.CreateRoomSuccessC(sid, x, s) =>
                    println(s"[${name}] create")
                    (x.toInt, s.sendBye("create").finish())
                case ChatProto1.RoomExistsC(sid, x, s) =>
                    println(s"[${name}] exists")
                    (x.toInt, s.sendBye("exists").finish())
            }
        joinRoom(d, d.rPid, apPort)
        done
    }

    def joinRoom(d: DataC, pid: Net.Pid, rAPPort: Net.Port): Unit = {
        // !!! rename 3x registerC in API
        d.rAPPort = rAPPort
        registerC3(this.port, "localhost", d.rAPPort +1, d, c3_1suspend)
    }

    def c3_1suspend(d: DataC, s: ChatProto3.C1Suspend): Done.type = {
        // !!! rename 3x registerC in API
        registerC(this.port, "localhost", d.rAPPort, d, c2_1)  // ChatProto2.registerC
        println(s"c3 suspend")
        s.suspend(d, c3_1)
    }

    def c3_1(d: DataC, s: ChatProto3.C1): Done.type = {
        s match {
            case ChatProto3.IncomingChatMessageC(sid, x, s) =>
                println(s"[${name}] received: ${x}")
                s.suspend(d, c3_1)
            case ChatProto3.ByeC(sid, x, s) =>
                if (!this.out.isUsed) {
                    //finishAndClose(this.out.get)  // !!! cannot end  // normally already ended by app logic (LeaveRoom), but maybe crash
                }
                finishAndClose(s)
        }
    }

    var out: Session.LinOption[ChatProto2.C1] = Session.LinNone()

    // !!! out stream -- cache/become necessary
    def c2_1(d: DataC, s: ChatProto2.C1): Done.type = {
        val done = c2_1aux(d, s)
        Util.spawn(() => timer(d))
        done
    }

    def c2_1aux(d: DataC, s: ChatProto2.C1): Done.type = {
        println(s"[${name}] sending: ${pid}")
        val s1 = s.sendOutgoingChatMessage(s"${pid}")  // !!! reassign
        /*val done = Session.cache(s1,
            (sid: Session.Sid, a: Actor) => proto2.C1(sid, a),
            (a: Some[proto2.C1]) => this.out = a)*/
        val (a, done) = Session.freeze(s, (sid: Session.Sid, a: Actor) => ChatProto2.C1(sid, a))
        this.out = a
        done
    }

    // async-become maybe nicer instead of spawn
    def timer(d: DataC): Unit = {
        Thread.sleep(2000)
        this.out match {
            case _: Session.LinNone => ()  // cf. error handling?
            case y: Session.LinSome[ChatProto2.C1] =>
                Session.become(d, y, c2_1aux)
        }
        timer(d)
    }
}
