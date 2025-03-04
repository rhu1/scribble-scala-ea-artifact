package ea.example.savina.fib

import ea.example.savina.fib.Proto1
import ea.example.savina.fib.Proto2
import ea.example.savina.pingself.TestPingSelf
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Net, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestFib {

    val PORT_Proto1 = 8888
    val PORT_M = 7777
    val PORT_F = 6666

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val ap_Proto1 = new Proto1.Proto1
        ap_Proto1.spawn(PORT_Proto1)

        Thread.sleep(500)

        //M.debug = true
        //F.debug = true
        M.main(Array());
        F.main(Array())

        //HERE HERE close
        for i <- 1 to 2 do println(s"Closed ${shutdown.take()}.")
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


case class DataC2() extends Session.Data {
    var reqx: Int = 0
    var c22: LinOption[Proto2.C22] = LinNone()
    var respx: Int = 0
}


/* "Main" */

case class Data_Main() extends Session.Data

object M extends Actor("MyM") with Proto1.ActorP {

    def main(args: Array[String]): Unit = {
        this.spawn(TestFib.PORT_M)
        this.registerP(TestFib.PORT_M, "localhost", TestFib.PORT_Proto1, Data_Main(), m1)
    }

    def m1(d: Data_Main, s: Proto1.P1): Done.type = {
        //println(s"(${s.sid}) A sending L1...")

        //s.sendRequest(0).suspend(d, m2)

        // 1 1 2 3 5 8 13
        //s.sendRequest(2).suspend(d, m2)
        //s.sendRequest(3).suspend(d, m2)
        //s.sendRequest(7).suspend(d, m2)
        s.sendRequest(10).suspend(d, m2)  // 55
        //s.sendRequest(15).suspend(d, m2)  // XXXX uses a lot of ports, port spam problem? or simply port in use?
    }
    
    def m2(d: Data_Main, s: Proto1.P2): Done.type =
        s match {
            case Proto1.ResponseP(sid, role, x, s) =>
                //println(s"(${sid}) A received L2.")
                //Thread.sleep(1000)
                //println(s"(${sid}) A sending L1...")
                println(s"${nameToString()} received Response: ${x}")
                finishAndClose(s)
        }

    override def afterClosed(): Unit = TestFib.shutdown.add(this.pid);

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestFib.handleException(cause, addr, sid)
}


/* ... */

// TODO consider distributed port allocation
object Ports {
    // !!! AtomicInteger
    var ports = 4444;
    def nextPort(): Int = {
        this.synchronized {
            ports = ports + 1
            //println(ports)
            ports
        }
    }
}


/* Top-most Fib actor */

case class DataB() extends Session.Data {
    var reqx: Int = 0
    var c2: LinOption[Proto1.C2] = LinNone()
    var respx: Int = 0
}

object F extends Actor("MyF") with Proto1.ActorC with Proto2.ActorP {

    def main(args: Array[String]): Unit = {
        this.spawn(TestFib.PORT_F)
        this.registerC(TestFib.PORT_F, "localhost", TestFib.PORT_Proto1, DataB(), c1Init)  // !!! mutable data
    }

    def c1Init(d: DataB, s: Proto1.C1Suspend): Done.type = {
        s.suspend(d, c1)
    }

    def c1(d: DataB, s: Proto1.C1): Done.type =
        s match {
            case Proto1.RequestC(sid, role, x, s) =>
                d.reqx = x
                if (d.reqx <= 2) {  // Asking for 1st or 2nd Fib number
                    finishAndClose(s.sendResponse(1))  // Close here or...*
                } else {

                    // freeze s -- !!! cf. just do inline input? (i.e., Fib2 Response) -- same guarantees as freeze/become? -- XXX would need to spawn parallel actor (as this event loop would be blocked) and do out-of-band input, e.g., local chan
                    // !!! revisit "callback stack" idea ?
                    val (f, done) = freeze(s, (sid, r, a) => Proto1.C2(sid, r, a))
                    d.c2 = f

                    val ap_Proto2 = new Proto2.Proto2
                    val port_Proto2 = Ports.nextPort()
                    ap_Proto2.spawn(port_Proto2)
                    Thread.sleep(500)
                    registerP(TestFib.PORT_F, "localhost", port_Proto2, d, p1)

                    val c1port = Ports.nextPort()
                    val c2port = Ports.nextPort()
                    new F1(s"F-1-${c1port}", c1port, port_Proto2).main(Array())  // !!! FIXME _ in names not allowed
                    new F2(s"F-2-${c2port}", c2port, port_Proto2).main(Array())

                    done
                }
        }

    def p1(d: DataB, s: Proto2.P1): Done.type =
        s.sendRequest1(d.reqx-1).sendRequest2(d.reqx-2).suspend(d, p3)

    def p3(d: DataB, s: Proto2.P3): Done.type =
        s match {
            case Proto2.Response1P(sid, role, x, s) =>
                d.respx = d.respx + x
                s.suspend(d, p4)
        }

    def p4(d: DataB, s: Proto2.P4): Done.type =
        s match {
            case Proto2.Response2P(sid, role, x, s) =>
                d.respx = d.respx + x
                d.c2 match {
                    case _: Session.LinNone =>
                    case c2: Session.LinSome[Proto1.C2] =>
                        become(d, c2, cb)
                }
                s.finish()
        }

    // TODO fresh APs not closed
    def cb(d: DataB, s: Proto1.C2): Done.type = finishAndClose(s.sendResponse(d.respx))  // *...or close here

    override def afterClosed(): Unit = TestFib.shutdown.add(this.pid);

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestFib.handleException(cause, addr, sid)
}


/* Use to create 1st child Fib actors */

case class DataC1() extends Session.Data {
    var reqx: Int = 0
    var c12: LinOption[Proto2.C12] = LinNone()
    var respx: Int = 0
}

class F1(pid: Net.Pid, port: Net.Port, aport: Net.Port) extends Actor(pid) with Proto2.ActorC1 with Proto2.ActorP {

    def main(args: Array[String]): Unit = {
        this.spawn(port)
        this.registerC1(port, "localhost", aport, DataC1(), c1Init)
    }

    def c1Init(d: DataC1, s: Proto2.C11Suspend): Done.type = {
        s.suspend(d, c1)
    }

    def c1(d: DataC1, s: Proto2.C11): Done.type = {
        s match {
            case Proto2.Request1C1(sid, role, x, s) =>
                d.reqx = x
                if (d.reqx <= 2) {  // Asking for 1st or 2nd Fib number
                    finishAndClose(s.sendResponse1(1))
                } else {
                    // !!! cf. freeze s?
                    val (f, done) = freeze(s, (sid, r, a) => Proto2.C12(sid, r, a))
                    d.c12 = f

                    // !!! factor out
                    val ap_Proto2 = new Proto2.Proto2
                    val port_Proto2 = Ports.nextPort()
                    ap_Proto2.spawn(port_Proto2)
                    Thread.sleep(500)
                    registerP(this.port, "localhost", port_Proto2, d, p1)

                    val c1port = Ports.nextPort()
                    val c2port = Ports.nextPort()
                    new F1(s"F-1-${c1port}", c1port, port_Proto2).main(Array())
                    new F2(s"F-2-${c2port}", c2port, port_Proto2).main(Array())

                    done
                }
        }
    }

    def p1(d: DataC1, s: Proto2.P1): Done.type =
        s.sendRequest1(d.reqx-1).sendRequest2(d.reqx-2).suspend(d, p3)

    def p3(d: DataC1, s: Proto2.P3): Done.type =
        s match {
            case Proto2.Response1P(sid, role, x, s) =>
                d.respx = d.respx + x
                s.suspend(d, p4)
        }

    def p4(d: DataC1, s: Proto2.P4): Done.type =
        s match {
            case Proto2.Response2P(sid, role, x, s) =>
                d.respx = d.respx + x
                d.c12 match {
                    case _: Session.LinNone => // !!! type case
                    case c2: Session.LinSome[Proto2.C12] =>
                        become(d, c2, cb)
                }
                s.finish()
        }

    def cb(d: DataC1, s: Proto2.C12): Done.type = finishAndClose(s.sendResponse1(d.respx))

    //override def afterClosed(): Unit = TestFib.shutdown.add(this.pid);

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestFib.handleException(cause, addr, sid)
}


/* Use to create 2nd child Fib actors */

class F2(pid: Net.Pid, port: Net.Port, aport: Net.Port) extends Actor(pid) with Proto2.ActorC2 with Proto2.ActorP {

    def main(args: Array[String]): Unit = {
        this.spawn(port)
        this.registerC2(port, "localhost", aport, DataC2(), c2Init)  // !!! mutable data
    }

    def c2Init(d: DataC2, s: Proto2.C21Suspend): Done.type = {
        s.suspend(d, c1)
    }

    def c1(d: DataC2, s: Proto2.C21): Done.type = {
        s match {
            case Proto2.Request2C2(sid, role, x, s) =>
                d.reqx = x
                if (d.reqx <= 2) {
                    finishAndClose(s.sendResponse2(1))
                } else {
                    // !!! cf. freeze s?
                    val (f, done) = freeze(s, (sid, r, a) => Proto2.C22(sid, r, a))
                    d.c22 = f

                    val ap_Proto2 = new Proto2.Proto2
                    val port_Proto2 = Ports.nextPort()
                    ap_Proto2.spawn(port_Proto2)
                    Thread.sleep(500)
                    this.registerP(this.port, "localhost", port_Proto2, d, p1)

                    val c1port = Ports.nextPort()
                    val c2port = Ports.nextPort()
                    new F1(s"F-1-${c1port}", c1port, port_Proto2).main(Array())
                    new F2(s"F-2-${c2port}", c2port, port_Proto2).main(Array())

                    done
                }
        }
    }

    def p1(d: DataC2, s: Proto2.P1): Done.type = {
        s.sendRequest1(d.reqx-1).sendRequest2(d.reqx-2).suspend(d, p3)
    }

    def p3(d: DataC2, s: Proto2.P3): Done.type = {
        s match {
            case Proto2.Response1P(sid, role, x, s) =>
                d.respx = d.respx + x
                s.suspend(d, p4)
        }
    }

    def p4(d: DataC2, s: Proto2.P4): Done.type = {
        s match {
            case Proto2.Response2P(sid, role, x, s) =>
                d.respx = d.respx + x
                d.c22 match {
                    case _: Session.LinNone =>
                    case c2: Session.LinSome[_] => become(d, c2, cb)
                }
                s.finish()
        }
    }

    def cb(d: DataC2, s: Proto2.C22): Done.type = finishAndClose(s.sendResponse2(d.respx))

    //override def afterClosed(): Unit = TestFib.shutdown.add(this.pid);

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestFib.handleException(cause, addr, sid)
}
