package ea.example.savina.fib

import ea.example.savina.fib.Fib.{Proto1, Proto2}
import ea.runtime.Net.{Pid, Port}
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.atomic.AtomicInteger


object TestFib {

    val PORT_Proto1: Port = 8888
    val PORT_M: Port = 7777
    val PORT_F: Port = 6666

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val ap_Proto1 = new Proto1.Proto1
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

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}


/* "Main" */

case class Data_Main() extends Session.Data

object M extends Actor("MyM") with Proto1.ActorP {

    def main(args: Array[String]): Unit =
        this.spawn(TestFib.PORT_M)
        this.registerP(TestFib.PORT_M, "localhost", TestFib.PORT_Proto1, Data_Main(), m1)

    def m1(d: Data_Main, s: Proto1.P1): Done.type =
        // 1 1 2 3 5 8 13 21 34 55
        s.sendRequest(10).suspend(d, m2)  // 55

    def m2(d: Data_Main, s: Proto1.P2): Done.type = s match {
        case Proto1.ResponseP(sid, role, x, s) =>
            println(s"${nameToString()} received Response: $x")
            finishAndClose(s)
    }

    override def afterClosed(): Unit = TestFib.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestFib.handleException(cause, addr, sid)
}


object Ports {
    private val ports = AtomicInteger(4444)
    private val proto2APs = collection.mutable.ListBuffer[Proto2.Proto2]()

    def nextPort(): Int = this.ports.incrementAndGet()

    def spawnFreshProto2AP(): Int =
        val ap_Proto2 = new Proto2.Proto2
        val port_Proto2 = nextPort()
        ap_Proto2.spawn(port_Proto2)
        this.proto2APs += ap_Proto2
        Thread.sleep(500)
        port_Proto2

    def closeAllProto2APs(): Unit = this.proto2APs.foreach(x => x.close())
}


/* Top-most Fib actor */

case class Data_F() extends Session.Data {
    var n_req: Int = 0
    var x_resp: Int = 0
    var c2: LinOption[Proto1.C2] = LinNone()
}

object F extends Actor("MyF") with Proto1.ActorC with Proto2.ActorP {

    def main(args: Array[String]): Unit =
        this.spawn(TestFib.PORT_F)
        this.registerC(TestFib.PORT_F, "localhost", TestFib.PORT_Proto1, Data_F(), c1Init)

    def c1Init(d: Data_F, s: Proto1.C1Suspend): Done.type = s.suspend(d, c1)

    def c1(d: Data_F, s: Proto1.C1): Done.type = s match {
        case Proto1.RequestC(sid, role, x, s) =>
            d.n_req = x
            if (d.n_req <= 2) {  // Asking for 1st or 2nd Fib number
                finishAndClose(s.sendResponse(1))  // Close here or...*
            } else {

                // freeze s -- !!! cf. just do inline input? (i.e., Fib2 Response) -- same guarantees as freeze/become? -- XXX would need to spawn parallel actor (as this event loop would be blocked) and do out-of-band input, e.g., local chan
                // !!! revisit "callback stack" idea ?
                val (f, done) = freeze(s, (sid, r, a) => Fib.Proto1.C2(sid, r, a))
                d.c2 = f

                val port_Proto2_fresh = Ports.spawnFreshProto2AP()
                registerP(TestFib.PORT_F, "localhost", port_Proto2_fresh, d, p1)

                val port_C1 = Ports.nextPort()
                val port_C2 = Ports.nextPort()
                new F1(s"F-1-$port_C1", port_C1, port_Proto2_fresh).main(Array())
                new F2(s"F-2-$port_C2", port_C2, port_Proto2_fresh).main(Array())

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

    def p4(d: Data_F, s: Proto2.P4): Done.type = s match {
        case Proto2.Response2P(sid, role, x, s) =>
            d.x_resp += x
            d.c2 match {
                case _: Session.LinNone =>
                case c2: Session.LinSome[_] =>
                    become(d, c2, cb)
            }
            s.finish()
    }

    private def cb(d: Data_F, s: Proto1.C2): Done.type =
        println(s"${nameToString()} sending F(${d.n_req}): ${d.x_resp}")
        finishAndClose(s.sendResponse(d.x_resp))
        // *...or close here

    override def afterClosed(): Unit = TestFib.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestFib.handleException(cause, addr, sid)
}


/* Use to create 1st child Fib actors */

case class Data_F1() extends Session.Data {
    var n_req: Int = 0
    var x_resp: Int = 0
    var c12: LinOption[Proto2.C12] = LinNone()
}

class F1(pid_F1: Pid, port_F1: Port, port_Proto2: Port)
    extends Actor(pid_F1) with Proto2.ActorC1 with Proto2.ActorP {

    def main(args: Array[String]): Unit =
        this.spawn(port_F1)
        this.registerC1(port_F1, "localhost", port_Proto2, Data_F1(), c1Init)

    def c1Init(d: Data_F1, s: Proto2.C11Suspend): Done.type = s.suspend(d, c1)

    def c1(d: Data_F1, s: Proto2.C11): Done.type = s match {
        case Proto2.Request1C1(sid, role, x, s) =>
            d.n_req = x
            if (d.n_req <= 2) {  // Asking for 1st or 2nd Fib number
                finishAndClose(s.sendResponse1(1))
            } else {
                val (f, done) = freeze(s, (sid, r, a) => Proto2.C12(sid, r, a))
                d.c12 = f

                val port_Proto2_fresh = Ports.spawnFreshProto2AP()
                registerP(this.port_F1, "localhost", port_Proto2_fresh, d, p1)

                val port_C1 = Ports.nextPort()
                val port_C2 = Ports.nextPort()
                new F1(s"F-1-$port_C1", port_C1, port_Proto2_fresh).main(Array())
                new F2(s"F-2-$port_C2", port_C2, port_Proto2_fresh).main(Array())

                done
            }
    }

    def p1(d: Data_F1, s: Proto2.P1): Done.type =
        s.sendRequest1(d.n_req-1).sendRequest2(d.n_req-2).suspend(d, p3)

    def p3(d: Data_F1, s: Proto2.P3): Done.type = s match {
        case Proto2.Response1P(sid, role, x, s) =>
            d.x_resp += x
            s.suspend(d, p4)
    }

    def p4(d: Data_F1, s: Proto2.P4): Done.type = s match {
        case Proto2.Response2P(sid, role, x, s) =>
            d.x_resp += x
            d.c12 match {
                case c2: Session.LinSome[_] =>  // Proto2.C12
                    become(d, c2, cb)
                case _: Session.LinNone => throw new RuntimeException("missing frozen")
            }
            s.finish()
    }

    private def cb(d: Data_F1, s: Proto2.C12): Done.type =
        println(s"${nameToString()} sending F1(${d.n_req}): ${d.x_resp}")
        finishAndClose(s.sendResponse1(d.x_resp))

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

class F2(pid_F2: Pid, port_F2: Port, port_Proto2: Port)
    extends Actor(pid_F2) with Proto2.ActorC2 with Proto2.ActorP {

    def main(args: Array[String]): Unit =
        this.spawn(port_F2)
        this.registerC2(port_F2, "localhost", port_Proto2, Data_F2(), c2Init)

    def c2Init(d: Data_F2, s: Proto2.C21Suspend): Done.type = s.suspend(d, c1)

    def c1(d: Data_F2, s: Proto2.C21): Done.type = s match {
        case Proto2.Request2C2(sid, role, x, s) =>
            d.n_req = x
            if (d.n_req <= 2) {
                finishAndClose(s.sendResponse2(1))  // Close here or...*
            } else {
                val (f, done) = freeze(s, (sid, r, a) => Proto2.C22(sid, r, a))
                d.c22 = f

                val port_Proto2_fresh = Ports.spawnFreshProto2AP()
                this.registerP(this.port_F2, "localhost", port_Proto2_fresh, d, p1)

                val port_C1 = Ports.nextPort()
                val port_C2 = Ports.nextPort()
                new F1(s"F-1-$port_C1", port_C1, port_Proto2_fresh).main(Array())
                new F2(s"F-2-$port_C2", port_C2, port_Proto2_fresh).main(Array())

                done
            }
    }

    def p1(d: Data_F2, s: Proto2.P1): Done.type =
        s.sendRequest1(d.n_req-1).sendRequest2(d.n_req-2).suspend(d, p3)

    def p3(d: Data_F2, s: Proto2.P3): Done.type = s match {
        case Proto2.Response1P(sid, role, x, s) =>
            d.x_resp += x
            s.suspend(d, p4)
    }

    def p4(d: Data_F2, s: Proto2.P4): Done.type = s match {
        case Proto2.Response2P(sid, role, x, s) =>
            d.x_resp += x
            d.c22 match {
                case _: Session.LinNone =>
                case c2: Session.LinSome[_] => become(d, c2, cb)
            }
            s.finish()
    }

    private def cb(d: Data_F2, s: Proto2.C22): Done.type =
        println(s"${nameToString()} sending F2(${d.n_req}): ${d.x_resp}")
        finishAndClose(s.sendResponse2(d.x_resp))
        // *...or close here

    //override def afterClosed(): Unit = TestFib.shutdown.add(this.pid);

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestFib.handleException(cause, addr, sid)
}
