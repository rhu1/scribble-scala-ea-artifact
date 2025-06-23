package ea.example.id

import ea.example.id.Id.Proto1
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Net, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestId {

    val PORT_Proto1 = 8888
    val PORT_S = 7777
    val PORT_C = 6666

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val ap_Proto1 = new Proto1.Proto1
        ap_Proto1.spawn(PORT_Proto1)

        Thread.sleep(500)

        //S.debug = true
        //C.debug = true
        S.main(Array());
        //while (true) {  // Non-terminating
        val cs = collection.mutable.ListBuffer[C]()
        while (shutdown.isEmpty) { // Non-terminating by default
            val port = Ports.nextPort()
            val c = new C(s"C-${port}", port)
            cs += c
            c.main(Array()) // !!! FIXME _ in names not allowed
            Thread.sleep(2000)
        }

        cs.foreach(_.enqueueClose())

        for i <- 1 to (cs.length + 1) do println(s"Closed ${shutdown.take()}.")  // S and Cs
        println(s"Closing ${ap_Proto1.nameToString()}...")
        ap_Proto1.close()
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* Ports */

// TODO consider distributed port allocation
object Ports {

    /*private val ports = AtomicInteger(3333);
    //private val proto2APs = collection.mutable.ListBuffer[Proto2.Proto2]()

    def nextPort(): Int = this.ports.incrementAndGet()

    def spawnFreshProto2AP(): Int = {
        val ap_Proto2 = new Proto2.Proto2
        val port_Proto2 = nextPort()
        ap_Proto2.spawn(port_Proto2)
        this.proto2APs += ap_Proto2
        Thread.sleep(500)
        port_Proto2
    }

    def closeAllProto2APs(): Unit = this.proto2APs.foreach(x => x.close())*/

    private var count = 4444;

    def nextPort(): Int =
        count += 1
        count
}


/* S */

object Data_S extends Session.Data  {
    private var x: Int = 0
    def get(): Int =
        x += 1
        x
}

object S extends Actor("MyS") with Proto1.ActorS {

    def main(args: Array[String]): Unit = {
        this.spawn(TestId.PORT_S)
        this.registerS(TestId.PORT_S, "localhost", TestId.PORT_Proto1, Data_S, s1Init)
    }

    def s1Init(d: Data_S.type, s: Proto1.S1Suspend): Done.type =
        this.registerS(TestId.PORT_S, "localhost", TestId.PORT_Proto1, Data_S, s1Init)
        s.suspend(d, s1)

    def s1(d: Data_S.type, s: Proto1.S1): Done.type =
        s match {
            case Proto1.IDRequestS(sid, role, x, s) =>
                s.sendIDResponse(d.get()).suspend(d, s1)
        }

    override def afterClosed(): Unit = TestId.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestId.handleException(cause, addr, sid)
}


/* C */

case class Data_C() extends Session.Data

class C(pid: Net.Pid, port: Net.Port) extends Actor(pid) with Proto1.ActorC {

    def main(args: Array[String]): Unit = {
        this.spawn(port)
        println(s"${nameToString()} registering ${port} ...")
        this.registerC(port, "localhost", TestId.PORT_Proto1, Data_C(), c1)
    }

    def c1(d: Data_C, s: Proto1.C1): Done.type =
        s.sendIDRequest(nameToString()).suspend(d, c2)

    def c2(d: Data_C, s: Proto1.C2): Done.type =
        s match {
            case Proto1.IDResponseC(sid, role, x, s) =>
                println(s"${nameToString()} received Id: ${x}")
                Thread.sleep(1000)
                c1(d, s)
        }

    override def afterClosed(): Unit = TestId.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestId.handleException(cause, addr, sid)
}
