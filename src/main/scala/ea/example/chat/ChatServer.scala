package ea.example.chat

import ea.example.chat.Chat.{Proto1, Proto2, Proto3}
import ea.runtime.Net.{Pid, Port}
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue
import scala.collection.mutable


class Data_S extends Session.Data {}

trait Registry extends Proto1.ActorS {}

object ChatServer extends Actor("Server") with Registry {

    val PORT_Proto1: Port = 9997
    private val PORT_S: Port = 8888

    private val rooms: mutable.Map[Pid, Port] = collection.mutable.Map[Pid, Port]()

    def spawn(): Unit =
        val d = new Data_S
        spawn(PORT_S)
        registerS(PORT_S, "localhost", PORT_Proto1, d, s1suspend)

    /* Proto1 */

    def s1suspend(d: Data_S, s: Proto1.S1Suspend): Done.type = {
        registerS(PORT_S, "localhost", PORT_Proto1, d, s1suspend)
        s.suspend(d, s1)
    }

    // For closing
    val shutdownRooms: LinkedTransferQueue[String] = LinkedTransferQueue()
    private val p2s = collection.mutable.ListBuffer[Proto2.Proto2]()
    private val p3s = collection.mutable.ListBuffer[Proto3.Proto3]()
    private val rs = collection.mutable.ListBuffer[ChatRoom]()

    def s1(d: Data_S, s: Proto1.S1): Done.type =
        s match {
            case Proto1.LookupRoomS(sid, role, pid_R, s) =>
                if (this.rooms.contains(pid_R)) {
                    val port_Proto2 = this.rooms(pid_R).toString
                    s.sendRoomPID(port_Proto2).suspend(d, s1)
                } else {
                    s.sendRoomNotFound(pid_R).suspend(d, s1)
                }
            case Proto1.CreateRoomS(sid, role, pid_R, s) =>
                if (this.rooms.contains(pid_R)) {
                    val port_Proto2 = this.rooms(pid_R).toString
                    s.sendRoomExists(port_Proto2).suspend(d, s1)
                } else {
                    val (port_Proto2, port_Proto3) = ServerPorts.nextAPPort()
                    val proto2 = new Proto2.Proto2
                    val proto3 = new Proto3.Proto3
                    p2s += proto2
                    p3s += proto3
                    //proto2.debug = true
                    //proto3.debug = true
                    proto2.spawn(port_Proto2)
                    proto3.spawn(port_Proto3)
                    Thread.sleep(1000)
                    this.rooms(pid_R) = port_Proto2

                    val port_R = ServerPorts.nextRoomPort()
                    val room = new ChatRoom(pid_R, port_R, port_Proto2, port_Proto3)
                    rs += room
                    //room.debug = true
                    room.spawn()

                    s.sendCreateRoomSuccess(s"${port_Proto2.toString}").suspend(d, s1)
                }
            case Proto1.ListRoomsS(sid, role, x, s) =>
                val list = this.rooms.keySet.mkString("::")
                s.sendRoomList(list).suspend(d, s1)
            case Proto1.ByeS(sid, role, x, s) =>
                s.finish()
    }

    /* Close */

    override def afterClosed(): Unit =
        println(s"Closing ${rs.map(_.nameToString()).mkString(", ")} ...")
        rs.foreach(_.enqueueClose())
        for i <- 1 to rs.length do println(s"Closed ${shutdownRooms.take()}.")
        println(s"Closing ${p2s.map(_.nameToString()).mkString(", ")} ...")
        p2s.foreach(_.close())
        println(s"Closing ${p3s.map(_.nameToString()).mkString(", ")} ...")
        p3s.foreach(_.close())
        TestChatServer.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}

object ServerPorts {
    private var apCounter = 9886
    private var rCounter = 6665

    def nextAPPort(): (Int, Int) =
        this.apCounter += 2 // !!!
        (apCounter, apCounter + 1)

    def nextRoomPort(): Int =
        this.rCounter += 1
        rCounter
}

