package ea.example.id

import ea.example.id.Id.Proto1
import ea.runtime.Net.{Pid, Port}
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestId {

    val PORT_Proto1: Port = S.PORT_Proto1

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val proto1 = new Proto1.Proto1
        proto1.spawn(PORT_Proto1)

        Thread.sleep(500)

        //S.debug = true
        S.main(Array())

        val cs = collection.mutable.ListBuffer[C]()
        while (shutdown.isEmpty) {  // Non-terminating by default
            val port = Ports_C.nextPort()
            val c = new C(s"C-$port", port, PORT_Proto1)
            cs += c
            c.main(Array()) // !!! FIXME _ in names not allowed
            Thread.sleep(2000)
        }

        cs.foreach(_.enqueueClose())

        for i <- 1 to (cs.length + 1) do println(s"Closed ${shutdown.take()}.")  // S and Cs
        println(s"Closing ${proto1.nameToString}...")
        proto1.close()
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}


/* S */

object Data_S extends Session.Data  {
    private var x: Int = 0
    def get(): Int =
        x += 1
        x
}

object S extends Actor("MyS") with Proto1.ActorS {

    val PORT_Proto1 = 8888
    private val PORT_S = 7777

    def main(args: Array[String]): Unit =
        this.spawn(PORT_S)
        this.registerS(PORT_S, "localhost", PORT_Proto1, Data_S, s1Init)

    def s1Init(d: Data_S.type, s: Proto1.S1Suspend): Done.type =
        this.registerS(PORT_S, "localhost", PORT_Proto1, Data_S, s1Init)
        s.suspend(d, s1)

    def s1(d: Data_S.type, s: Proto1.S1): Done.type =
        s match {
            case Proto1.IDRequestS(sid, role, x, s) =>
                s.sendIDResponse(d.get()).suspend(d, s1)
        }

    /* Close */

    override def afterClosed(): Unit = TestId.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestId.handleException(cause, addr, sid)
}


/* C */

object Ports_C {
    private var count = 4444

    def nextPort(): Int =
        count += 1
        count
}

case class Data_C() extends Session.Data

class C(pid: Pid, port_C: Port, port_Proto1: Port) extends Actor(pid) with Proto1.ActorC {

    def main(args: Array[String]): Unit = {
        println(s"$nameToString Spawning $port_C ...")
        this.spawn(port_C)
        this.registerC(port_C, "localhost", port_Proto1, Data_C(), c1)
    }

    def c1(d: Data_C, s: Proto1.C1): Done.type =
        s.sendIDRequest(nameToString).suspend(d, c2)

    def c2(d: Data_C, s: Proto1.C2): Done.type = s match {
        case Proto1.IDResponseC(sid, role, x, s) =>
            println(s"$nameToString Received Id: $x")
            Thread.sleep(1000)
            c1(d, s)
        }

    /* Close */

    override def afterClosed(): Unit = TestId.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestId.handleException(cause, addr, sid)
}
