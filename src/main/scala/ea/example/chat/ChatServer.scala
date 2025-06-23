package ea.example.chat

import ea.runtime.{Actor, Done, Net, Session}
import ea.runtime.Net.{Pid, Port}
import ea.example.chat.Chat.{Proto1, Proto2, Proto3}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue
import scala.collection.mutable


object TestChatServer {

    val PORT_Proto1 = 9997
    val PORT_S = 8888

    val PORT_C1 = 7777
    val PORT_C2 = 7779

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        println("hello")

        val ap1 = new Proto1.Proto1
        //ap1.debug = true
        ap1.spawn(PORT_Proto1)

        val server = new ChatServer("Server", PORT_S)
        //server.debug = true
        server.spawn()

        val c1 = new ChatClient("client1", PORT_C1)
        //c1.debug = true
        val d1 = c1.spawn()
        c1.run(d1, TestChatServer.PORT_Proto1)

        val c2 = new ChatClient("client2", PORT_C2)
        //c2.debug = true
        val d2 = c2.spawn()
        c2.run(d2, TestChatServer.PORT_Proto1)

        // Only waiting for server -- but fake, server protocol is non-terminating
        println(s"Closed ${shutdown.take()}.")
        println(s"Closing ${ap1.nameToString()}...")
        ap1.close()
    }
}

object ServerPorts {
    private var apCounter = 9886
    private var rCounter = 6665

    def nextAPPort(): (Int, Int) = {
        this.apCounter += 2 // !!!
        (apCounter, apCounter + 1)
    }

    def nextRoomPort(): Int = {
        this.rCounter += 1
        rCounter
    }
}

/* ... */

class DataS extends Session.Data {
    // Move to ChatServer (not per session data)
    val rooms: mutable.Map[Pid, Port] = collection.mutable.Map[Net.Pid, Net.Port]()
}

trait Registry extends Proto1.ActorS

class ChatServer(pid: Net.Pid, port: Net.Port) extends Actor(pid) with Registry {

    def spawn(): Unit = {
        val d = new DataS
        spawn(this.port)
        registerS(this.port, "localhost", TestChatServer.PORT_Proto1, d, s1suspend)
    }

    def s1suspend(d: DataS, s: Proto1.S1Suspend): Done.type = {
        registerS(this.port, "localhost", TestChatServer.PORT_Proto1, d, s1suspend)
        s.suspend(d, s1)
    }


    // cf. TestFib Ports spawnFreshProto2AP / closeAllProto2APs
    private val ap2s = collection.mutable.ListBuffer[Proto2.Proto2]()
    private val ap3s = collection.mutable.ListBuffer[Proto3.Proto3]()

    def s1(d: DataS, s: Proto1.S1): Done.type = {
        s match {
            case Proto1.LookupRoomS(sid, role, x, s) =>
                val pid = x
                if (d.rooms.contains(pid)) {
                    val apPort = d.rooms(pid).toString
                    s.sendRoomPID(apPort).suspend(d, s1)  // !!! send port
                } else {
                    s.sendRoomNotFound(pid).suspend(d, s1)
                }
            case Proto1.CreateRoomS(sid, role, x, s) =>
                val pid = x
                if (d.rooms.contains(pid)) {
                    val apPort = d.rooms(pid).toString
                    s.sendRoomExists(apPort).suspend(d, s1)
                } else {
                    val (apPort1, apPort2) = ServerPorts.nextAPPort()
                    val ap2 = new Proto2.Proto2
                    val ap3 = new Proto3.Proto3
                    ap2s :+ ap2
                    ap3s :+ ap3
                    //ap2.debug = true
                    //ap3.debug = true
                    ap2.spawn(apPort1)
                    ap3.spawn(apPort2)
                    Thread.sleep(1000)
                    d.rooms(pid) = apPort1

                    val rPort = ServerPorts.nextRoomPort()
                    val room = new ChatRoom(pid, rPort, apPort1, apPort2)
                    //room.debug = true
                    room.spawn()

                    s.sendCreateRoomSuccess(s"${apPort1.toString}").suspend(d, s1)
                }
            case Proto1.ListRoomsS(sid, role, x, s) =>
                val list = d.rooms.keySet.mkString("::")
                s.sendRoomList(list).suspend(d, s1)
            case Proto1.ByeS(sid, role, x, s) =>
                //finishAndClose(s)  // XXX
                s.finish()
        }
    }

    override def afterClosed(): Unit =
        println(s"Closing ${ap2s} ...")
        ap2s.foreach(_.close())
        println(s"Closing ${ap3s} ...")
        ap3s.foreach(_.close())
        TestChatServer.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}

