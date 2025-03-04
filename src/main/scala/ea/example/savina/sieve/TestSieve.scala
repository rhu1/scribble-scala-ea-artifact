package ea.example.savina.sieve

import ea.example.savina.sieve.Sieve.Proto1
import ea.example.savina.sieve.Sieve.Proto2
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Net, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.atomic.AtomicInteger

object TestSieve {

    val PORT_Proto1 = 8888
    val PORT_Proto2 = 9999
    val PORT_M = 7777
    val PORT_G = 6666
    val PORT_F1 = 5555

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val ap_Proto1 = new Proto1.Proto1
        ap_Proto1.spawn(PORT_Proto1)

        Thread.sleep(500)

        //M.debug = true
        //G.debug = true
        //F1.debug = true
        M.main(Array());
        G.main(Array())
        F1.main(Array())
        //F.main(Array())
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* ... */

case class DataA() extends Session.Data

object M extends Actor("MyM") with Proto1.ActorM {

    def main(args: Array[String]): Unit = {
        this.spawn(TestSieve.PORT_M)
        this.registerM(TestSieve.PORT_M, "localhost", TestSieve.PORT_Proto1, DataA(), m1)
    }

    def m1(d: DataA, s: Proto1.M1): Done.type = s.sendStart().suspend(d, m2)

    def m2(d: DataA, s: Proto1.M2): Done.type =
        s match {
            case Proto1.ExitM(sid, role, s) =>
                //Thread.sleep(1000)
                finishAndClose(s)
        }

    //override def afterClosed(): Unit = TestDining.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestSieve.handleException(cause, addr, sid)
}


/* ... */

case class DataB() extends Session.Data

object G extends Actor("MyG") with Proto1.ActorG {

    def main(args: Array[String]): Unit = {
        spawn(TestSieve.PORT_G)
        registerG(TestSieve.PORT_G, "localhost", TestSieve.PORT_Proto1, DataB(), g1Suspend)
    }

    def g1Suspend(d: DataB, s: Proto1.G1Suspend): Done.type = {
       s.suspend(d, g1)
    }

    def g1(d: DataB, s: Proto1.G1): Done.type = {
            //println(s"(${s.sid}) A sending L1...")
        s match {
            case Proto1.StartG(sid, role, s) =>
                var s2 = s.sendNewPrime(2)
                var candidate: Int = 3
                while (candidate < 30) {
                    s2 = s2.sendLongBox(candidate)
                    candidate = candidate + 2
                }
                s2.sendExit().suspend(d, g4)
        }
    }

    def g4(d: DataB, s: Proto1.G4): Done.type = {
        //println(s"(${s.sid}) A sending L1...")
        s match {
            case Proto1.AckG(sid, role, s) =>
                val end = s.sendExit()
                Thread.sleep(500)
                finishAndClose(end)
        }
    }

    //override def afterClosed(): Unit = TestDining.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestSieve.handleException(cause, addr, sid)
}


/* ... */

case class DataC() extends Session.Data {
    //var c22: LinOption[Fib2.C22] = LinNone()
    var f3: LinOption[Proto2.F3] = LinNone()
    var x: Int = -1
    var newPrime: Int = -1
}

object F1 extends Actor("MyF1") with Proto1.ActorF1 with Proto2.ActorF {

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
        this.spawn(5555)
        this.registerF1(5555, "localhost", 8888, DataC(), f11Init)
    }

    def f11Init(d: DataC, s: Proto1.F11Suspend): Done.type = {
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

    def f3LongBox(d: DataC, s: Proto2.F3): Done.type = {
        val s1 = s.sendLongBox2(d.x)
        val (a, done) = freeze(s1, (sid, r, a) => Proto2.F3(sid, r, a))
        d.f3 = a
        done
    }

    def storeLocally(): Boolean = availableLocalPrimes < numMaxLocalPrimes

    def f11(d: DataC, s: Proto1.F11): Done.type = {
        s match {
            case Proto1.NewPrimeF1(sid, role, x, s) =>
                // save x in local primes
                localPrimes(availableLocalPrimes) = x  // i = 0
                availableLocalPrimes = availableLocalPrimes + 1
                s.suspend(d, f12)
        }
    }

    def exit(d: DataC, s: Proto2.F3): Done.type = {
        /*val end = s.sendExit2()
        Thread.sleep(500)
        end.finish()*/
        println(s"F1 waiting for Ack...")
        s.sendExit2().suspend(d, f4)
    }

    def f4(d: DataC, s: Proto2.F4): Done.type = {
        s match {
            case Proto2.Ack2F(sid, role, s) =>
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
            case y: Session.LinSome[Proto2.F3] =>
                become(d, y, exit)
        }
    }

    def f12(d: DataC, s: Proto1.F12): Done.type = {

        println(s"F1 received ${s}")

        s match {
            case Proto1.ExitF1(sid, role, s) =>
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

            case Proto1.LongBoxF1(sid, role, x, s) => {
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
                            case y: Session.LinSome[Proto2.F3] =>
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

                                /*val p2 = new Proto2.Proto2
                                //p2.debug = true
                                val bport = Ports.nextPort()
                                p2.spawn(bport) // !!! close afterwards

                                println(s"F1 111")
                                Thread.sleep(500)*/
                                val bport = Ports.spawnFreshProto2AP()
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

    def f1Init(d: DataC, s: Proto2.F1Suspend): Done.type = {
        s.suspend(d, f1)
    }

    // !!! register is event-driven, so this cannot be done inline with F spawning
    def f1(d: DataC, s: Proto2.F1): Done.type = {
        s match {
            case Proto2.ReadyF(sid, role, s) =>
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
                    val (a, done) = freeze(s3, (sid, r, a) => Proto2.F3(sid, r, a))
                    d.f3 = a
                    done
                }
        }
    }

    //override def afterClosed(): Unit = TestDining.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestSieve.handleException(cause, addr, sid)
}


/* ... */

// TODO consider distributed port allocation
object Ports {

    private val ports = AtomicInteger(3333);
    private val proto2APs = collection.mutable.ListBuffer[Proto2.Proto2]()

    def nextPort(): Int = this.ports.addAndGet(2)

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


/* ... */

case class DataD() extends Session.Data {
    var f3: LinOption[Proto2.F3] = LinNone()
    var x: Int = -1
    var newPrime: Int = -1
}

class F(pid: Net.Pid, port: Net.Port, aport: Net.Port) extends Actor(pid) with Proto2.ActorF with Proto2.ActorFnext {

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

    def n1Init(d: DataD, s: Proto2.Fnext1): Done.type = {
        println(s"F bbb")
        s.sendReady().suspend(d, n2)
    }

    def n2(d: DataD, s: Proto2.Fnext2): Done.type = {
        s match {
            case Proto2.NewPrimeFnext(sid, role, x, s) =>
                println(s"F got new prime ${x}")
                // save x in locals
                localPrimes(availableLocalPrimes) = x  // i = 0
                availableLocalPrimes = availableLocalPrimes + 1
                s.suspend(d, n3)
        }
    }

    def exit(d: DataD, s: Proto2.F3): Done.type = {
        //val end = s.sendExit2()
        //Thread.sleep(500)
        //end.finish()
        s.sendExit2().suspend(d, f4)
    }

    def f4(d: DataD, s: Proto2.F4): Done.type = {
        s match {
            case Proto2.Ack2F(sid, role, s) =>
                Thread.sleep(500)
                finishAndClose(s)
        }
    }

    def n3(d: DataD, s: Proto2.Fnext3): Done.type = {
        s match {
            case Proto2.Exit2Fnext(sid, role, s) =>
                //localPrimes.foreach(x => print(s"${x} "))
                (0 until availableLocalPrimes).foreach(x => print(s"${localPrimes(x)} "))
                println()

                if (readyNext) {
                    d.f3 match {
                        case _: Session.LinNone => throw new RuntimeException("missing frozen")
                        case y: Session.LinSome[Proto2.F3] =>
                            become(d, y, exit)
                    }
                } else {
                    this.pendingExit = true
                }

                val done = s.sendAck2().finish() // !!! `exit` is doing close... (waits for Ack2 from Fnext)
                Thread.sleep(500)
                done

            case Proto2.LongBox2Fnext(sid, role, x, s) => {
                //println(s"(${sid}) B received L1.")
                //println(s"(${sid}) B sending L2...")

                // if locally prime and [ if has Fnext become Proto2.F and send Longbox
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
                            case y: Session.LinSome[Proto2.F3] =>
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

                                /*val p2 = new Proto2.Proto2
                                val bport = Ports.nextPort()
                                p2.spawn(bport) // !!! close afterwards

                                Thread.sleep(500)*/
                                val bport = Ports.spawnFreshProto2AP()
                                registerF(port, "localhost", bport, d, f1Init)

                                val nport = Ports.nextPort()
                                new F(s"F-${nport}", nport, bport).main(Array()) // !!! FIXME _ in names not allowed

                                hasNext = true
                            }
                        }
                    }

                    //val (a, done) = freeze(s, (sid, r, a) => Proto1.F11(sid, r, a))
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

    def f3LongBox(d: DataD, s: Proto2.F3): Done.type = {
        val s1 = s.sendLongBox2(d.x)
        val (a, done) = freeze(s1, (sid, r, a) => Proto2.F3(sid, r, a))
        d.f3 = a
        done
    }

    def storeLocally(): Boolean = availableLocalPrimes < numMaxLocalPrimes

    def f1Init(d: DataD, s: Proto2.F1Suspend): Done.type = {
        s.suspend(d, f1)
    }

    def f1(d: DataD, s: Proto2.F1): Done.type = {
        s match {
            case Proto2.ReadyF(sid, role, s) =>
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
                    val (a, done) = freeze(s3, (sid, r, a) => Proto2.F3(sid, r, a))
                    d.f3 = a
                    done
                }
        }
    }

    //override def afterClosed(): Unit = TestDining.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestSieve.handleException(cause, addr, sid)
}
