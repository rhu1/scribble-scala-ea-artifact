package tmp.EATmp.Chat

import ea.runtime.{Actor, Done, Net, Session}
import ea.runtime.Net.{Pid, Port}
import tmp.EATmp.{ChatProto1, ChatProto2, ChatProto3}

import java.net.SocketAddress
import scala.collection.mutable


object TestChatServer {

    def main(args: Array[String]): Unit = {
        println("hello")

        val ap1 = new ChatProto1.ChatProto1
        //proto3.debug = true
        ap1.spawn(9997)

        val server = new ChatServer("Server", 8888)
        server.debug = true
        server.spawn()
    }
}


/* ... */

class DataS extends Session.Data {
    // Move to ChatServer (not per session data)
    val rooms: mutable.Map[Pid, Port] = collection.mutable.Map[Net.Pid, Net.Port]()
    var apCounter = 9886
    var rCounter = 6665
    def nextAPPort(): Int = {
        this.apCounter += 2  // !!!
        apCounter
    }
    def nextRoomPort(): Int = {
        this.rCounter += 1
        rCounter
    }
}

trait Registry extends ChatProto1.ActorS

class ChatServer(pid: Net.Pid, port: Net.Port) extends Actor(pid) with Registry {

    def spawn(): Unit = {
        val d = new DataS
        spawn(this.port)
        registerS(this.port, "localhost", 9997, d, s1suspend)
    }

    def s1suspend(d: DataS, s: ChatProto1.S1Suspend): Done.type = {
        registerS(this.port, "localhost", 9997, d, s1suspend)
        s.suspend(d, s1)
    }

    def s1(d: DataS, s: ChatProto1.S1): Done.type = {
        s match {
            case ChatProto1.LookupRoomS(sid, x, s) =>
                val pid = x
                if (d.rooms.contains(pid)) {
                    val apPort = d.rooms(pid).toString
                    s.sendRoomPID(apPort).suspend(d, s1)  // !!! send port
                } else {
                    s.sendRoomNotFound(pid).suspend(d, s1)
                }
            case ChatProto1.CreateRoomS(sid, x, s) =>
                val pid = x
                if (d.rooms.contains(pid)) {
                    val apPort = d.rooms(pid).toString
                    s.sendRoomExists(apPort).suspend(d, s1)
                } else {
                    val apPort = d.nextAPPort()
                    val ap2 = new ChatProto2.ChatProto2
                    val ap3 = new ChatProto3.ChatProto3
                    //ap2.debug = true
                    //ap3.debug = true
                    ap2.spawn(apPort)
                    ap3.spawn(apPort+1)
                    Thread.sleep(1000)
                    d.rooms(pid) = apPort

                    val rPort = d.nextRoomPort()
                    val room = new ChatRoom(pid, rPort, apPort)
                    room.spawn()

                    s.sendCreateRoomSuccess(s"${apPort.toString}").suspend(d, s1)
                }
            case ChatProto1.ListRoomsS(sid, x, s) =>
                val list = d.rooms.keySet.mkString("::")
                s.sendRoomList(list).suspend(d, s1)
            case ChatProto1.ByeS(sid, x, s) =>
                finishAndClose(s)
        }
    }

    override def handleException(addr: SocketAddress): Unit = {
        println(s"Channel exception from: ${addr}")
    }
}

