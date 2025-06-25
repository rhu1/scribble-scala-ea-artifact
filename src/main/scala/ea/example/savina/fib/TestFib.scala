package ea.example.savina.fib

import ea.example.savina.fib.Fib.Proto1.{ActorC, ActorP, C1, C1Suspend, C2, P1, P2, Proto1, RequestC, ResponseP}
import ea.example.savina.fib.Fib.Proto2
import ea.example.savina.fib.Fib.Proto2.{ActorC1, C11, C11Suspend, C12, Request1C1}
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Net, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.atomic.AtomicInteger


object TestFib {

    val PORT_Proto1 = 8888
    val PORT_M = 7777
    val PORT_F = 6666

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val ap_Proto1 = new Proto1
        ap_Proto1.spawn(PORT_Proto1)

        Thread.sleep(500)

        //M.debug = true
        //F.debug = true
        F.main(Array())
        M.main(Array())

        for i <- 1 to 2 do println(s"Closed ${shutdown.take()}.")  // M and F
        println(s"Closing ${ap_Proto1.nameToString()}...")
        ap_Proto1.close()
        println(s"Closing all Proto2 APs...")
        Ports.closeAllProto2APs()
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* Fresh ports and Proto2 APs */

// TODO consider distributed port allocation
object Ports {

    private val ports = AtomicInteger(4444);
    private val proto2APs = collection.mutable.ListBuffer[Proto2.Proto2]()

    def nextPort(): Int = this.ports.incrementAndGet()

    def spawnFreshProto2AP(): Int = {
        val ap_Proto2 = new Proto2.Proto2
        val port_Proto2 = nextPort()
        ap_Proto2.spawn(port_Proto2)
        this.proto2APs += ap_Proto2
        Thread.sleep(500)
        port_Proto2
    }

    def closeAllProto2APs(): Unit = this.proto2APs.foreach(x => x.close())
}


/* "Main" */

case class Data_Main() extends Session.Data

object M extends Actor("MyM") with ActorP {

    def main(args: Array[String]): Unit = {
        this.spawn(TestFib.PORT_M)
        this.registerP(TestFib.PORT_M, "localhost", TestFib.PORT_Proto1, Data_Main(), m1)
    }

    def m1(d: Data_Main, s: P1): Done.type = {
        // 1 1 2 3 5 8 13 21 34 55
        //s.sendRequest(2).suspend(d, m2)
        //s.sendRequest(3).suspend(d, m2)
        //s.sendRequest(7).suspend(d, m2)
        s.sendRequest(10).suspend(d, m2)  // 55
        //s.sendRequest(15).suspend(d, m2)  // XXX uses a lot of ports, port spam problem? or simply port in use?
    }
    
    def m2(d: Data_Main, s: P2): Done.type =
        s match {
            case ResponseP(sid, role, x, s) =>
                //println(s"(${sid}) A received L2.")
                //Thread.sleep(1000)
                //println(s"(${sid}) A sending L1...")
                println(s"${nameToString()} received Response: ${x}")
                finishAndClose(s)
        }

    override def afterClosed(): Unit = TestFib.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestFib.handleException(cause, addr, sid)
}


/* Top-most Fib actor */

case class Data_F() extends Session.Data {
    var n_req: Int = 0
    var x_resp: Int = 0
    var c2: LinOption[C2] = LinNone()
}

object F extends Actor("MyF") with ActorC with Proto2.ActorP {

    def main(args: Array[String]): Unit = {
        this.spawn(TestFib.PORT_F)
        this.registerC(TestFib.PORT_F, "localhost", TestFib.PORT_Proto1, Data_F(), c1Init)
    }

    def c1Init(d: Data_F, s: C1Suspend): Done.type = {
        s.suspend(d, c1)
    }

    def c1(d: Data_F, s: C1): Done.type =
        s match {
            case RequestC(sid, role, x, s) =>
                d.n_req = x
                if (d.n_req <= 2) {  // Asking for 1st or 2nd Fib number
                    finishAndClose(s.sendResponse(1))  // Close here or...*
                } else {

                    // freeze s -- !!! cf. just do inline input? (i.e., Fib2 Response) -- same guarantees as freeze/become? -- XXX would need to spawn parallel actor (as this event loop would be blocked) and do out-of-band input, e.g., local chan
                    // !!! revisit "callback stack" idea ?
                    val (f, done) = freeze(s, (sid, r, a) => Fib.Proto1.C2(sid, r, a))
                    d.c2 = f

                    val port_Proto2 = Ports.spawnFreshProto2AP()
                    registerP(TestFib.PORT_F, "localhost", port_Proto2, d, p1)

                    val c1port = Ports.nextPort()
                    val c2port = Ports.nextPort()
                    new F1(s"F-1-${c1port}", c1port, port_Proto2).main(Array())  // !!! FIXME _ in names not allowed
                    new F2(s"F-2-${c2port}", c2port, port_Proto2).main(Array())

                    done
                }
        }

    def p1(d: Data_F, s: Proto2.P1): Done.type =
        s.sendRequest1(d.n_req-1).sendRequest2(d.n_req-2).suspend(d, p3)

    def p3(d: Data_F, s: Proto2.P3): Done.type =
        s match {
            case Proto2.Response1P(sid, role, x, s) =>
                d.x_resp += x
                s.suspend(d, p4)
        }

    def p4(d: Data_F, s: Proto2.P4): Done.type =
        s match {
            case Proto2.Response2P(sid, role, x, s) =>
                d.x_resp += x
                d.c2 match {
                    case _: Session.LinNone =>
                    case c2: Session.LinSome[_] =>
                        become(d, c2, cb)
                }
                s.finish()
        }

    def cb(d: Data_F, s: C2): Done.type = {
        println(s"${nameToString()} sending F(${d.n_req}): ${d.x_resp}")
        finishAndClose(s.sendResponse(d.x_resp))
    }  // *...or close here

    override def afterClosed(): Unit = TestFib.shutdown.add(this.pid);

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestFib.handleException(cause, addr, sid)
}


/* Use to create 1st child Fib actors */

case class Data_F1() extends Session.Data {
    var n_req: Int = 0
    var x_resp: Int = 0
    var c12: LinOption[C12] = LinNone()
}

class F1(pid: Net.Pid_C, port: Net.Port, aport: Net.Port) extends Actor(pid) with ActorC1 with Proto2.ActorP {

    def main(args: Array[String]): Unit = {
        this.spawn(port)
        this.registerC1(port, "localhost", aport, Data_F1(), c1Init)
    }

    def c1Init(d: Data_F1, s: C11Suspend): Done.type = {
        s.suspend(d, c1)
    }

    def c1(d: Data_F1, s: C11): Done.type = {
        s match {
            case Request1C1(sid, role, x, s) =>
                d.n_req = x
                if (d.n_req <= 2) {  // Asking for 1st or 2nd Fib number
                    finishAndClose(s.sendResponse1(1))
                } else {
                    val (f, done) = freeze(s, (sid, r, a) => Proto2.C12(sid, r, a))
                    d.c12 = f

                    val port_Proto2 = Ports.spawnFreshProto2AP()
                    registerP(this.port, "localhost", port_Proto2, d, p1)

                    val c1port = Ports.nextPort()
                    val c2port = Ports.nextPort()
                    new F1(s"F-1-${c1port}", c1port, port_Proto2).main(Array())
                    new F2(s"F-2-${c2port}", c2port, port_Proto2).main(Array())

                    done
                }
        }
    }

    def p1(d: Data_F1, s: Proto2.P1): Done.type =
        s.sendRequest1(d.n_req-1).sendRequest2(d.n_req-2).suspend(d, p3)

    def p3(d: Data_F1, s: Proto2.P3): Done.type =
        s match {
            case Proto2.Response1P(sid, role, x, s) =>
                d.x_resp += x
                s.suspend(d, p4)
        }

    def p4(d: Data_F1, s: Proto2.P4): Done.type =
        s match {
            case Proto2.Response2P(sid, role, x, s) =>
                d.x_resp += x
                d.c12 match {
                    case c2: Session.LinSome[_] =>  // Proto2.C12
                        become(d, c2, cb)
                    case _: Session.LinNone => throw new RuntimeException("missing frozen")
                }
                s.finish()
        }

    def cb(d: Data_F1, s: Proto2.C12): Done.type = {
        println(s"${nameToString()} sending F1(${d.n_req}): ${d.x_resp}")
        finishAndClose(s.sendResponse1(d.x_resp))
    }

    //override def afterClosed(): Unit = TestFib.shutdown.add(this.pid);

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestFib.handleException(cause, addr, sid)
}


/* Use to create 2nd child Fib actors */

case class Data_F2() extends Session.Data {
    var n_req: Int = 0
    var x_resp: Int = 0
    var c22: LinOption[Proto2.C22] = LinNone()
}

class F2(pid: Net.Pid_C, port: Net.Port, aport: Net.Port) extends Actor(pid) with Proto2.ActorC2 with Proto2.ActorP {

    def main(args: Array[String]): Unit = {
        this.spawn(port)
        this.registerC2(port, "localhost", aport, Data_F2(), c2Init)
    }

    def c2Init(d: Data_F2, s: Proto2.C21Suspend): Done.type = {
        s.suspend(d, c1)
    }

    def c1(d: Data_F2, s: Proto2.C21): Done.type = {
        s match {
            case Proto2.Request2C2(sid, role, x, s) =>
                d.n_req = x
                if (d.n_req <= 2) {
                    finishAndClose(s.sendResponse2(1))  // Close here or...*
                } else {
                    val (f, done) = freeze(s, (sid, r, a) => Proto2.C22(sid, r, a))
                    d.c22 = f

                    val port_Proto2 = Ports.spawnFreshProto2AP()
                    this.registerP(this.port, "localhost", port_Proto2, d, p1)

                    val c1port = Ports.nextPort()
                    val c2port = Ports.nextPort()
                    new F1(s"F-1-${c1port}", c1port, port_Proto2).main(Array())
                    new F2(s"F-2-${c2port}", c2port, port_Proto2).main(Array())

                    done
                }
        }
    }

    def p1(d: Data_F2, s: Proto2.P1): Done.type = {
        s.sendRequest1(d.n_req-1).sendRequest2(d.n_req-2).suspend(d, p3)
    }

    def p3(d: Data_F2, s: Proto2.P3): Done.type = {
        s match {
            case Proto2.Response1P(sid, role, x, s) =>
                d.x_resp += x
                s.suspend(d, p4)
        }
    }

    def p4(d: Data_F2, s: Proto2.P4): Done.type = {
        s match {
            case Proto2.Response2P(sid, role, x, s) =>
                d.x_resp += x
                d.c22 match {
                    case _: Session.LinNone =>
                    case c2: Session.LinSome[_] => become(d, c2, cb)
                }
                s.finish()
        }
    }

    def cb(d: Data_F2, s: Proto2.C22): Done.type = {
        println(s"${nameToString()} sending F2(${d.n_req}): ${d.x_resp}")
        finishAndClose(s.sendResponse2(d.x_resp))
    }  // *...or close here

    //override def afterClosed(): Unit = TestFib.shutdown.add(this.pid);

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestFib.handleException(cause, addr, sid)
}
