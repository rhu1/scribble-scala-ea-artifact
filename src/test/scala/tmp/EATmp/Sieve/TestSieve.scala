package tmp.EATmp.Sieve

import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Net, Session}
import tmp.EATmp.{Sieve1, Sieve2}

import java.net.SocketAddress

object TestFib {

    def main(args: Array[String]): Unit = {
        println("Hello")

        val p1 = new Sieve1.Sieve1
        p1.spawn(8888)
        Thread.sleep(1000)

        //M.debug = true
        //G.debug = true
        //F1.debug = true

        M.main(Array());
        G.main(Array())
        F1.main(Array())
        //F.main(Array())
    }
}


case class DataA() extends Session.Data
case class DataB() extends Session.Data {
}
case class DataC() extends Session.Data {
    //var c22: LinOption[Fib2.C22] = LinNone()
    var f2: LinOption[Sieve2.F2] = LinNone()
    var x: Int = -1
    var newPrime: Int = -1
}
case class DataD() extends Session.Data {
    var f2: LinOption[Sieve2.F2] = LinNone()
    var x: Int = -1
    var newPrime: Int = -1
}


/* ... */

object M extends Actor("MyM") with Sieve1.ActorM {

    def main(args: Array[String]): Unit = {
        spawn(7777)
        registerM(7777, "localhost", 8888, DataA(), m1)
    }

    def m1(d: DataA, s: Sieve1.M1): Done.type = {
        //println(s"(${s.sid}) A sending L1...")
        s.sendStart().suspend(d, m2)
    }
    
    def m2(d: DataA, s: Sieve1.M2): Done.type = {
        s match {
            case Sieve1.ExitM(sid, role, s) =>
                //println(s"(${sid}) A received L2.")
                Thread.sleep(1000)
                //println(s"(${sid}) A sending L1...")
                //println(s"(${sid}) M received: ${x}")
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

object G extends Actor("MyG") with Sieve1.ActorG {

    def main(args: Array[String]): Unit = {
        spawn(6666)
        registerG(6666, "localhost", 8888, DataB(), g1Suspend)
    }

    def g1Suspend(d: DataB, s: Sieve1.G1Suspend): Done.type = {
       s.suspend(d, g1)
    }

    def g1(d: DataB, s: Sieve1.G1): Done.type = {
            //println(s"(${s.sid}) A sending L1...")
        s match {
            case Sieve1.StartG(sid, role, s) =>
                var s2 = s.sendNewPrime(2)
                var candidate: Int = 3
                while (candidate < 20) {
                    s2 = s2.sendLongBox(candidate)
                    candidate = candidate + 2
                }
                s2.sendExit().suspend(d, g4)
        }
    }

    def g4(d: DataB, s: Sieve1.G4): Done.type = {
        //println(s"(${s.sid}) A sending L1...")
        s match {
            case Sieve1.AckG(sid, role, s) =>
                val end = s.sendExit()
                Thread.sleep(500)
                finishAndClose(end)
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
    var ports = 3333;
    //val lock = new Object()

    def nextPort(): Int = {
        this.synchronized {
            ports = ports + 2  // !!! AP needs two ports?
            ports
        }
    }
}


/* ... */

object F1 extends Actor("MyF1") with Sieve1.ActorF1 with Sieve2.ActorF {

    val numMaxLocalPrimes = 3
    //var nextFilterActor: ActorRef = null
    var hasNext: Boolean = false
    val localPrimes = new Array[Long](numMaxLocalPrimes)

    var availableLocalPrimes = 0

    def main(args: Array[String]): Unit = {
        spawn(5555)
        registerF1(5555, "localhost", 8888, DataC(), f11Init)
    }

    def f11Init(d: DataC, s: Sieve1.F11Suspend): Done.type = {
        s.suspend(d, f11)
    }

    //def locallyPrime(x: Int): Boolean = true
    def isLocallyPrime(candidate: Long, localPrimes: Array[Long], startInc: Int, endExc: Int): Boolean = {
        for (i <- startInc until endExc) {
            val remainder = candidate % localPrimes(i)
            if (remainder == 0) return false
        }
        true
    }

    def f2LongBox(d: DataC, s: Sieve2.F2): Done.type = {
        val s1 = s.sendLongBox2(d.x)
        val (a, done) = freeze(s1, (sid, r, a) => Sieve2.F2(sid, r, a))
        d.f2 = a
        done
    }

    def storeLocally(): Boolean = availableLocalPrimes < numMaxLocalPrimes

    def f11(d: DataC, s: Sieve1.F11): Done.type = {
        s match {
            case Sieve1.NewPrimeF1(sid, role, x, s) =>
                // save x in local primes
                localPrimes(availableLocalPrimes) = x  // i = 0
                availableLocalPrimes = availableLocalPrimes + 1
                s.suspend(d, f12)
        }
    }

    def exit(d: DataC, s: Sieve2.F2): Done.type = {
        /*val end = s.sendExit2()
        Thread.sleep(500)
        end.finish()*/
        println(s"F1 waiting for Ack...")
        s.sendExit2().suspend(d, f3)
    }

    def f3(d: DataC, s: Sieve2.F3): Done.type = {
        s match {
            case Sieve2.Ack2F(sid, role, s) =>
                println("F1 got Ack, closing")
                Thread.sleep(500)
                finishAndClose(s)
        }
    }

    def f12(d: DataC, s: Sieve1.F12): Done.type = {

        println(s"F1 received ${s}")

        s match {
            case Sieve1.ExitF1(sid, role, s) =>
                // become Sieve2.F and send Exit
                localPrimes.foreach(x => print(s"${x} "))
                d.f2 match {
                    case _: Session.LinNone => throw new RuntimeException("missing frozen")  HERE resuspend if no conn yet
                    case y: Session.LinSome[Sieve2.F2] =>
                        become(d, y, exit)
                }
                Thread.sleep(500)

                // !!! XXX cannot close until Fnext done... -- in generally need close permission ack msg
                //finishAndClose(s)
                val done = s.sendAck().finish()  // !!! `exit` is doing close... (waits for Ack2 from Fnext)
                Thread.sleep(500)
                done

            case Sieve1.LongBoxF1(sid, role, x, s) => {
                //println(s"(${sid}) B received L1.")
                //println(s"(${sid}) B sending L2...")

                // if locally prime and [ if has Fnext become Sieve2.F and send Longbox
                //                        else if space store as locally prime else spawn Fnext ]
                // else skip
                //if (locallyPrime(x)) {

                println(s"F1 locallyPrime ${x} ${isLocallyPrime(x, localPrimes, 0, availableLocalPrimes)}")
                if (isLocallyPrime(x, localPrimes, 0, availableLocalPrimes)) {
                    if (hasNext) {
                        println(s"F1 should pass ${x}...")
                        // become
                        d.f2 match {
                            case _: Session.LinNone => throw new RuntimeException("missing frozen")
                            case y: Session.LinSome[Sieve2.F2] =>
                                println(s"F1 passing ${x}")
                                d.x = x
                                become(d, y, f2LongBox)
                        }
                    } else {
                        if (storeLocally()) {
                            localPrimes(availableLocalPrimes) = x
                            availableLocalPrimes = availableLocalPrimes + 1
                            println(s"F1 stored locally ${x}, ${availableLocalPrimes}")
                        } else {
                            println(s"F1 spawning next ${x}")
                            d.newPrime = x

                            val p2 = new Sieve2.Sieve2
                            val bport = Ports.nextPort()
                            p2.spawn(bport) // !!! close afterwards

                            println(s"F1 111")
                            Thread.sleep(500)
                            registerF(5555, "localhost", bport, d, f1)

                            println(s"F1 222 ${bport}")
                            val port = Ports.nextPort()
                            new F(s"F-${port}", port, bport).main(Array()) // !!! FIXME _ in names not allowed

                            Thread.sleep(500)
                            println(s"F1 333")
                            hasNext = true
                        }
                    }

                    //val (a, done) = freeze(s, (sid, r, a) => Sieve1.F11(sid, r, a))
                    //done
                }

                s.suspend(d, f12)
            }
        }
    }

    def f1(d: DataC, s: Sieve2.F1): Done.type = {
        val s2 = s.sendNewPrime(d.newPrime)
        val (a, done) = freeze(s2, (sid, r, a) => Sieve2.F2(sid, r, a))
        d.f2 = a
        done
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* ... */

class F(pid: Net.Pid, port: Net.Port, aport: Net.Port) extends Actor(pid) with Sieve2.ActorF with Sieve2.ActorFnext {

    val numMaxLocalPrimes = 3
    //var nextFilterActor: ActorRef = null
    var hasNext: Boolean = false
    val localPrimes = new Array[Long](numMaxLocalPrimes)

    var availableLocalPrimes = 0

    def main(args: Array[String]): Unit = {
        spawn(port)
        registerFnext(port, "localhost", aport, DataD(), n1Init)
        println(s"F aaa ${aport}")
    }

    def n1Init(d: DataD, s: Sieve2.Fnext1Suspend): Done.type = {
        println(s"F bbb")
        s.suspend(d, n1)
    }

    def n1(d: DataD, s: Sieve2.Fnext1): Done.type = {
        s match {
            case Sieve2.NewPrimeFnext(sid, role, x, s) =>
                println(s"F got new prime ${x}")
                // save x in locals
                localPrimes(availableLocalPrimes) = x  // i = 0
                availableLocalPrimes = availableLocalPrimes + 1
                s.suspend(d, n2)
        }
    }

    def exit(d: DataD, s: Sieve2.F2): Done.type = {
        //val end = s.sendExit2()
        //Thread.sleep(500)
        //end.finish()
        s.sendExit2().suspend(d, f3)
    }

    def f3(d: DataD, s: Sieve2.F3): Done.type = {
        s match {
            case Sieve2.Ack2F(sid, role, s) =>
                Thread.sleep(500)
                finishAndClose(s)
        }
    }

    def n2(d: DataD, s: Sieve2.Fnext2): Done.type = {
        s match {
            case Sieve2.Exit2Fnext(sid, role, s) =>
                localPrimes.foreach(x => print(s"${x} "))
                d.f2 match {
                    case _: Session.LinNone => throw new RuntimeException("missing frozen")
                    case y: Session.LinSome[Sieve2.F2] =>
                        become(d, y, exit)
                }

                val done = s.sendAck2().finish()  // !!! `exit` is doing close... (waits for Ack2 from Fnext)
                Thread.sleep(500)
                done

            case Sieve2.LongBox2Fnext(sid, role, x, s) => {
                //println(s"(${sid}) B received L1.")
                //println(s"(${sid}) B sending L2...")

                // if locally prime and [ if has Fnext become Sieve2.F and send Longbox
                //                        else if space store as locally prime else spawn Fnext ]
                // else skip
                //if (locallyPrime(x)) {
                println(s"F locallyPrime ${x} ${isLocallyPrime(x, localPrimes, 0, availableLocalPrimes)}")
                if (isLocallyPrime(x, localPrimes, 0, availableLocalPrimes)) {
                    if (hasNext) {
                        println(s"F passing ${x}")
                        // become
                        d.f2 match {
                            case _: Session.LinNone => throw new RuntimeException("missing frozen")
                            case y: Session.LinSome[Sieve2.F2] =>
                                d.x = x
                                become(d, y, f2LongBox)
                        }
                    } else {
                        if (storeLocally()) {
                            localPrimes(availableLocalPrimes) = x // i = 0
                            availableLocalPrimes = availableLocalPrimes + 1
                            println(s"F stored locally ${x}, ${availableLocalPrimes}")
                        } else {
                            println(s"F spawning next ${x}")
                            d.newPrime = x

                            val p2 = new Sieve2.Sieve2
                            val bport = Ports.nextPort()
                            p2.spawn(bport) // !!! close afterwards

                            Thread.sleep(500)
                            registerF(port, "localhost", bport, d, f1)

                            val nport = Ports.nextPort()
                            new F(s"F-${nport}", nport, bport).main(Array()) // !!! FIXME _ in names not allowed

                            hasNext = true
                        }
                    }

                    //val (a, done) = freeze(s, (sid, r, a) => Sieve1.F11(sid, r, a))
                    //done
                }

                s.suspend(d, n2)
            }
        }
    }

    //def locallyPrime(x: Int): Boolean = true
    def isLocallyPrime(candidate: Long, localPrimes: Array[Long], startInc: Int, endExc: Int): Boolean = {
        for (i <- startInc until endExc) {
            val remainder = candidate % localPrimes(i)
            if (remainder == 0) return false
        }
        true
    }

    def f2LongBox(d: DataD, s: Sieve2.F2): Done.type = {
        val s1 = s.sendLongBox2(d.x)
        val (a, done) = freeze(s1, (sid, r, a) => Sieve2.F2(sid, r, a))
        d.f2 = a
        done
    }

    def storeLocally(): Boolean = availableLocalPrimes < numMaxLocalPrimes

    def f1(d: DataD, s: Sieve2.F1): Done.type = {
        val s2 = s.sendNewPrime(d.newPrime)
        val (a, done) = freeze(s2, (sid, r, a) => Sieve2.F2(sid, r, a))
        d.f2 = a
        done
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}
