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
    var f3: LinOption[Sieve2.F3] = LinNone()
    var x: Int = -1
    var newPrime: Int = -1
}
case class DataD() extends Session.Data {
    var f3: LinOption[Sieve2.F3] = LinNone()
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

    // TODO Data -- also factor out with F
    val buff: collection.mutable.ListBuffer[Int] = collection.mutable.ListBuffer()
    var readyNext: Boolean = false
    var pendingExit: Boolean = false

    // TODO Data
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

    def f3LongBox(d: DataC, s: Sieve2.F3): Done.type = {
        val s1 = s.sendLongBox2(d.x)
        val (a, done) = freeze(s1, (sid, r, a) => Sieve2.F3(sid, r, a))
        d.f3 = a
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

    def exit(d: DataC, s: Sieve2.F3): Done.type = {
        /*val end = s.sendExit2()
        Thread.sleep(500)
        end.finish()*/
        println(s"F1 waiting for Ack...")
        s.sendExit2().suspend(d, f4)
    }

    def f4(d: DataC, s: Sieve2.F4): Done.type = {
        s match {
            case Sieve2.Ack2F(sid, role, s) =>
                println("F1 got Ack, closing")
                Thread.sleep(500)
                finishAndClose(s)
        }
    }

    def exitMatch(d: DataC): Done.type = {
        d.f3 match {
            case _: Session.LinNone =>
                println(s"aaaaaaaa: $hasNext $readyNext")
                throw new RuntimeException("missing frozen")
            case y: Session.LinSome[Sieve2.F3] =>
                become(d, y, exit)
        }
    }

    def f12(d: DataC, s: Sieve1.F12): Done.type = {

        println(s"F1 received ${s}")

        s match {
            case Sieve1.ExitF1(sid, role, s) =>
                // become Sieve2.F and send Exit
                //localPrimes.foreach(x => print(s"${x} "))
                (0 until availableLocalPrimes).foreach(x => print(s"${localPrimes(x)} "))
                println()

                // !!! Actor(MyF1) Read from /127.0.0.1: 50834: SEND_Sieve1_1_G_F1_LongBox_3.SEND_Sieve1_1_G_F1_LongBox_5.SEND_Sieve1_1_G_F1_LongBox_7.SEND_Sieve1_1_G_F1_LongBox_9.SEND_Sieve1_1_G_F1_LongBox_11.SEND_Sieve1_1_G_F1_LongBox_13.SEND_Sieve1_1_G_F1_LongBox_15.SEND_Sieve1_1_G_F1_LongBox_17.SEND_Sieve1_1_G_F1_LongBox_19.SEND_Sieve1_1_G_F1_Exit_.
                if (readyNext) {
                    exitMatch(d)
                    Thread.sleep(500)
                } else {
                    this.pendingExit = true  // CHECKME cf. theory?  concurrency between (async) register and become (of expected frozen)
                }

                // TODO freeze for pendingExit?
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
                    //if (hasNext) {  // HERE should be "next ready"
                    if (readyNext) { // HERE should be "next ready"
                            println(s"F1 should pass ${x}...")
                        // become
                        d.f3 match {
                            case _: Session.LinNone =>
                                println(s"bbbbbbb ${x}")
                                // HERE "resuspend" if no conn yet XXX
                                // - add next connected ACK to proto2, cf. hasNext -> nextReady XXX register fired is ready
                                // - if !nextReady and !storeLocally -> if !spawned do spawn else buffer for register next -- in register send all buffered
                                // !!! because of inline become? cf. async become would fire when registered -- !!! CHECKME registering multiple becomes?
                                throw new RuntimeException("missing frozen")  // HERE add exception to other examples
                            case y: Session.LinSome[Sieve2.F3] =>
                                println(s"F1 passing ${x}")
                                d.x = x
                                become(d, y, f3LongBox)
                        }
                    } else {
                        if (storeLocally()) {
                            localPrimes(availableLocalPrimes) = x
                            availableLocalPrimes = availableLocalPrimes + 1
                            println(s"F1 stored locally ${x}, ${availableLocalPrimes}")
                        } else {

                            if (hasNext) {  // && !readyNext
                                this.buff += x  // TODO put in d, cf. d.newPrime
                                //throw new RuntimeException("HERE")
                            } else {
                                println(s"F1 spawning next ${x}")
                                //d.newPrime = x
                                this.buff += x

                                val p2 = new Sieve2.Sieve2
                                //p2.debug = true
                                val bport = Ports.nextPort()
                                p2.spawn(bport) // !!! close afterwards

                                println(s"F1 111")
                                Thread.sleep(500)
                                registerF(5555, "localhost", bport, d, f1Init)

                                println(s"F1 222 ${bport}")
                                val port = Ports.nextPort()
                                new F(s"F-${port}", port, bport).main(Array()) // !!! FIXME _ in names not allowed

                                Thread.sleep(500)
                                println(s"F1 333")
                                hasNext = true
                            }
                        }
                    }

                    //val (a, done) = freeze(s, (sid, r, a) => Sieve1.F11(sid, r, a))
                    //done
                }

                s.suspend(d, f12)
            }
        }
    }

    def f1Init(d: DataC, s: Sieve2.F1Suspend): Done.type = {
        s.suspend(d, f1)
    }

    // !!! register is event-driven, so this cannot be done inline with F spawning
    def f1(d: DataC, s: Sieve2.F1): Done.type = {
        s match {
            case Sieve2.ReadyF(sid, role, s) =>
                this.readyNext = true

                //println(s"F1 sending new prime ${d.newPrime}")
                //val s2 = s.sendNewPrime(d.newPrime)
                println(s"F1 sending new prime ${this.buff}")
                var s3 = s.sendNewPrime(this.buff(0))
                for (i <- 1 until this.buff.length) {
                    println(s"F1 sending longbox $i ${this.buff(i)}")
                    s3 = s3.sendLongBox2(this.buff(i))
                }

                if (this.pendingExit) {
                    exit(d, s3)
                } else {
                    val (a, done) = freeze(s3, (sid, r, a) => Sieve2.F3(sid, r, a))
                    d.f3 = a
                    done
                }
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

class F(pid: Net.Pid, port: Net.Port, aport: Net.Port) extends Actor(pid) with Sieve2.ActorF with Sieve2.ActorFnext {

    val numMaxLocalPrimes = 3
    //var nextFilterActor: ActorRef = null
    var hasNext: Boolean = false

    // TODO Data -- also factor out with F1
    val buff: collection.mutable.ListBuffer[Int] = collection.mutable.ListBuffer()
    var readyNext: Boolean = false
    var pendingExit: Boolean = false

    val localPrimes = new Array[Long](numMaxLocalPrimes)

    var availableLocalPrimes = 0

    def main(args: Array[String]): Unit = {
        spawn(port)
        registerFnext(port, "localhost", aport, DataD(), n1Init)
        println(s"F aport ${aport}")
    }

    def n1Init(d: DataD, s: Sieve2.Fnext1): Done.type = {
        println(s"F bbb")
        s.sendReady().suspend(d, n2)
    }

    def n2(d: DataD, s: Sieve2.Fnext2): Done.type = {
        s match {
            case Sieve2.NewPrimeFnext(sid, role, x, s) =>
                println(s"F got new prime ${x}")
                // save x in locals
                localPrimes(availableLocalPrimes) = x  // i = 0
                availableLocalPrimes = availableLocalPrimes + 1
                s.suspend(d, n3)
        }
    }

    def exit(d: DataD, s: Sieve2.F3): Done.type = {
        //val end = s.sendExit2()
        //Thread.sleep(500)
        //end.finish()
        s.sendExit2().suspend(d, f4)
    }

    def f4(d: DataD, s: Sieve2.F4): Done.type = {
        s match {
            case Sieve2.Ack2F(sid, role, s) =>
                Thread.sleep(500)
                finishAndClose(s)
        }
    }

    def n3(d: DataD, s: Sieve2.Fnext3): Done.type = {
        s match {
            case Sieve2.Exit2Fnext(sid, role, s) =>
                //localPrimes.foreach(x => print(s"${x} "))
                (0 until availableLocalPrimes).foreach(x => print(s"${localPrimes(x)} "))
                println()

                if (readyNext) {
                    d.f3 match {
                        case _: Session.LinNone => throw new RuntimeException("missing frozen")
                        case y: Session.LinSome[Sieve2.F3] =>
                            become(d, y, exit)
                    }
                } else {
                    this.pendingExit = true
                }

                val done = s.sendAck2().finish() // !!! `exit` is doing close... (waits for Ack2 from Fnext)
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
                    if (readyNext) {
                        println(s"F passing ${x}")
                        // become
                        d.f3 match {
                            case _: Session.LinNone => throw new RuntimeException("missing frozen")
                            case y: Session.LinSome[Sieve2.F3] =>
                                d.x = x
                                become(d, y, f3LongBox)
                        }
                    } else {
                        if (storeLocally()) {
                            localPrimes(availableLocalPrimes) = x // i = 0
                            availableLocalPrimes = availableLocalPrimes + 1
                            println(s"F stored locally ${x}, ${availableLocalPrimes}")
                        } else {

                            if (hasNext) {
                                this.buff += x
                            } else {
                                println(s"F spawning next ${x}")
                                //d.newPrime = x
                                this.buff += x

                                val p2 = new Sieve2.Sieve2
                                val bport = Ports.nextPort()
                                p2.spawn(bport) // !!! close afterwards

                                Thread.sleep(500)
                                registerF(port, "localhost", bport, d, f1Init)

                                val nport = Ports.nextPort()
                                new F(s"F-${nport}", nport, bport).main(Array()) // !!! FIXME _ in names not allowed

                                hasNext = true
                            }
                        }
                    }

                    //val (a, done) = freeze(s, (sid, r, a) => Sieve1.F11(sid, r, a))
                    //done
                }

                s.suspend(d, n3)
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

    def f3LongBox(d: DataD, s: Sieve2.F3): Done.type = {
        val s1 = s.sendLongBox2(d.x)
        val (a, done) = freeze(s1, (sid, r, a) => Sieve2.F3(sid, r, a))
        d.f3 = a
        done
    }

    def storeLocally(): Boolean = availableLocalPrimes < numMaxLocalPrimes

    def f1Init(d: DataD, s: Sieve2.F1Suspend): Done.type = {
        s.suspend(d, f1)
    }

    def f1(d: DataD, s: Sieve2.F1): Done.type = {
        s match {
            case Sieve2.ReadyF(sid, role, s) =>
                this.readyNext = true

                //val s3 = s.sendNewPrime(d.newPrime)
                println(s"F1 sending new prime ${this.buff}")
                var s3 = s.sendNewPrime(this.buff(0))
                for (i <- 1 until this.buff.length) {
                    println(s"F1 sending longbox $i ${this.buff(i)}")
                    s3 = s3.sendLongBox2(this.buff(i))
                }

                if (this.pendingExit) {
                    exit(d, s3)
                } else {
                    val (a, done) = freeze(s3, (sid, r, a) => Sieve2.F3(sid, r, a))
                    d.f3 = a
                    done
                }
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}
