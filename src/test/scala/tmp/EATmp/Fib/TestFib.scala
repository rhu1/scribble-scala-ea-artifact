package tmp.EATmp.Fib

import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Net, Session}
import tmp.EATmp.{Fib1, Fib2}

import java.net.SocketAddress

object TestFib {

    def main(args: Array[String]): Unit = {
        println("Hello")

        val p1 = new Fib1.Fib1
        p1.spawn(8888)
        //val p2 = new Fib2.Fib2
        //p1.spawn(8888)
        Thread.sleep(1000)

        M.debug = true
        F.debug = true

        M.main(Array());
        F.main(Array())
    }
}


case class DataA() extends Session.Data
case class DataB() extends Session.Data {
    var reqx: Int = 0
    //var c2: LinOption[Fib1.C2] = LinNone()
    var c2: LinOption[Fib1.C2] = LinNone()
    var respx: Int = 0
}
case class DataC1() extends Session.Data {
    var reqx: Int = 0
    var c12: LinOption[Fib2.C12] = LinNone()
    var respx: Int = 0
}
case class DataC2() extends Session.Data {
    var reqx: Int = 0
    var c22: LinOption[Fib2.C22] = LinNone()
    var respx: Int = 0
}


/* ... */

object M extends Actor("MyM") with Fib1.ActorP {

    def main(args: Array[String]): Unit = {
        spawn(7777)
        registerP(7777, "localhost", 8888, DataA(), m1)  // !!! mutable data
    }

    def m1(d: DataA, s: Fib1.P1): Done.type = {
        //println(s"(${s.sid}) A sending L1...")

        //s.sendRequest(0).suspend(d, m2)

        // 1 1 2 3 5 8 13
        //s.sendRequest(2).suspend(d, m2)
        //s.sendRequest(3).suspend(d, m2)
        //s.sendRequest(7).suspend(d, m2)
        //s.sendRequest(10).suspend(d, m2)  // 55
        s.sendRequest(11).suspend(d, m2)
    }
    
    def m2(d: DataA, s: Fib1.P2): Done.type = {
        s match {
            case Fib1.ResponseP(sid, role, x, s) =>
                //println(s"(${sid}) A received L2.")
                //Thread.sleep(1000)
                //println(s"(${sid}) A sending L1...")
                println(s"(${sid}) M received: ${x}")
                finishAndClose(s)
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* ... */

// FIXME need distributed port allocation
object Ports {
    // cf. AtomicInteger
    var ports = 4444;
    //val lock = new Object()

    def nextPort(): Int = {
        if (ports >= 5555) {
            throw new RuntimeException("Out of ports...")
        }
        this.synchronized {
            ports = ports + 1
            ports
        }
    }
}

object F extends Actor("MyF") with Fib1.ActorC with Fib2.ActorP {

    def main(args: Array[String]): Unit = {
        spawn(6666)
        registerC(6666, "localhost", 8888, DataB(), c1Init)  // !!! mutable data
    }

    def c1Init(d: DataB, s: Fib1.C1Suspend): Done.type = {
        s.suspend(d, c1)
    }

    def c1(d: DataB, s: Fib1.C1): Done.type = {
        s match {
            case Fib1.RequestC(sid, role, x, s) =>
                //println(s"(${sid}) B received L1.")
                //println(s"(${sid}) B sending L2...")

                d.reqx = x
                val done = if (d.reqx <= 2) {
                    s.sendResponse(1).finish()
                } else {

                    // freeze s -- !!! cf. just do inline input? (i.e., Fib2 Response) -- same guarantees as freeze/become? -- XXX would need to spawn parallel actor (as this event loop would be blocked) and do out-of-band input, e.g., local chan
                    // !!! revisit "callback stack" idea ?

                    val (f, done) = freeze(s, (sid, r, a) => Fib1.C2(sid, r, a))
                    d.c2 = f

                    val p2 = new Fib2.Fib2
                    val aport = Ports.nextPort()
                    p2.spawn(aport)

                    Thread.sleep(500)
                    registerP(6666, "localhost", aport, d, p1)

                    val c1port = Ports.nextPort()
                    val c2port = Ports.nextPort()
                    new F1(s"F-1-${c1port}", c1port, aport).main(Array())  // !!! FIXME _ in names not allowed
                    new F2(s"F-2-${c2port}", c2port, aport).main(Array())

                    done
                }
                done
        }
    }

    def p1(d: DataB, s: Fib2.P1): Done.type = {
        s.sendRequest1(d.reqx-1).sendRequest2(d.reqx-2).suspend(d, p3)
    }

    def p3(d: DataB, s: Fib2.P3): Done.type = {
        s match {
            case Fib2.Response1P(sid, role, x, s) =>
                d.respx = d.respx + x
                s.suspend(d, p4)
        }
    }

    def p4(d: DataB, s: Fib2.P4): Done.type = {
        s match {
            case Fib2.Response2P(sid, role, x, s) =>
                d.respx = d.respx + x
                d.c2 match {
                    case _: Session.LinNone => // !!! type case
                    case c2: Session.LinSome[Fib1.C2] =>
                        become(d, c2, cb)
                }
                s.finish()
        }
    }

    def cb(d: DataB, s: Fib1.C2): Done.type = {
        s.sendResponse(d.respx).finish()
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}

class F1(pid: Net.Pid, port: Net.Port, aport: Net.Port) extends Actor(pid) with Fib2.ActorC1 with Fib2.ActorP {

    def main(args: Array[String]): Unit = {
        spawn(port)
        registerC1(port, "localhost", aport, DataC1(), c1Init)  // !!! mutable data
    }

    def c1Init(d: DataC1, s: Fib2.C11Suspend): Done.type = {
        s.suspend(d, c1)
    }

    def c1(d: DataC1, s: Fib2.C11): Done.type = {
        s match {
            case Fib2.Request1C1(sid, role, x, s) =>

                d.reqx = x
                val done = if (d.reqx <= 2) {
                    s.sendResponse1(1).finish()
                } else {
                    // !!! cf. freeze s?
                    val (f, done) = freeze(s, (sid, r, a) => Fib2.C12(sid, r, a))
                    d.c12 = f

                    val p2 = new Fib2.Fib2
                    val aport = Ports.nextPort()
                    p2.spawn(aport)

                    Thread.sleep(500)
                    registerP(this.port, "localhost", aport, d, p1)

                    val c1port = Ports.nextPort()
                    val c2port = Ports.nextPort()
                    new F1(s"F-1-${c1port}", c1port, aport).main(Array())
                    new F2(s"F-2-${c2port}", c2port, aport).main(Array())

                    done
                }
                done
        }
    }

    def p1(d: DataC1, s: Fib2.P1): Done.type = {
        s.sendRequest1(d.reqx-1).sendRequest2(d.reqx-2).suspend(d, p3)
    }

    def p3(d: DataC1, s: Fib2.P3): Done.type = {
        s match {
            case Fib2.Response1P(sid, role, x, s) =>
                d.respx = d.respx + x
                s.suspend(d, p4)
        }
    }

    def p4(d: DataC1, s: Fib2.P4): Done.type = {
        s match {
            case Fib2.Response2P(sid, role, x, s) =>
                d.respx = d.respx + x
                d.c12 match {
                    case _: Session.LinNone => // !!! type case
                    case c2: Session.LinSome[Fib2.C12] =>
                        become(d, c2, cb)
                }
                s.finish()
        }
    }

    def cb(d: DataC1, s: Fib2.C12): Done.type = {
        s.sendResponse1(d.respx).finish()
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}

class F2(pid: Net.Pid, port: Net.Port, aport: Net.Port) extends Actor(pid) with Fib2.ActorC2 with Fib2.ActorP {

    def main(args: Array[String]): Unit = {
        spawn(port)
        registerC2(port, "localhost", aport, DataC2(), c2Init)  // !!! mutable data
    }

    def c2Init(d: DataC2, s: Fib2.C21Suspend): Done.type = {
        s.suspend(d, c1)
    }

    def c1(d: DataC2, s: Fib2.C21): Done.type = {
        s match {
            case Fib2.Request2C2(sid, role, x, s) =>
                d.reqx = x
                val done = if (d.reqx <= 2) {
                    s.sendResponse2(1).finish()
                } else {
                    // !!! cf. freeze s?
                    val (f, done) = freeze(s, (sid, r, a) => Fib2.C22(sid, r, a))
                    d.c22 = f

                    val p2 = new Fib2.Fib2
                    val aport = Ports.nextPort()
                    p2.spawn(aport)

                    Thread.sleep(500)
                    registerP(this.port, "localhost", aport, d, p1)

                    val c1port = Ports.nextPort()
                    val c2port = Ports.nextPort()
                    new F1(s"F-1-${c1port}", c1port, aport).main(Array())
                    new F2(s"F-2-${c2port}", c2port, aport).main(Array())

                    done
                }
                done
        }
    }

    def p1(d: DataC2, s: Fib2.P1): Done.type = {
        s.sendRequest1(d.reqx-1).sendRequest2(d.reqx-2).suspend(d, p3)
    }

    def p3(d: DataC2, s: Fib2.P3): Done.type = {
        s match {
            case Fib2.Response1P(sid, role, x, s) =>
                d.respx = d.respx + x
                s.suspend(d, p4)
        }
    }

    def p4(d: DataC2, s: Fib2.P4): Done.type = {
        s match {
            case Fib2.Response2P(sid, role, x, s) =>
                d.respx = d.respx + x
                d.c22 match {
                    case _: Session.LinNone => // !!! type case
                    case c2: Session.LinSome[Fib2.C22] =>
                        become(d, c2, cb)
                }
                s.finish()
        }
    }

    def cb(d: DataC2, s: Fib2.C22): Done.type = {
        s.sendResponse2(d.respx).finish()
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}
