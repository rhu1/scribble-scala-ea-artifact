package ea.example.savina.sieve

import ea.example.savina.sieve.Sieve.{Proto1, Proto2}
import ea.runtime.Net.{Pid, Port}
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.atomic.AtomicInteger

object TestSieve {

    val PORT_Proto1: Port = 8888
    val PORT_Proto2: Port = 9999
    val PORT_M: Port = 7777
    val PORT_G: Port = 6666
    val PORT_F1: Port = 5555

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val proto1 = new Proto1.Proto1
        proto1.spawn(PORT_Proto1)

        Thread.sleep(500)

        //M.debug = true
        //G.debug = true
        //F1.debug = true
        M.main(Array())
        G.main(Array())
        F1.main(Array())
        //F.main(Array())

        for i <- 1 to 3 do println(s"Closed ${shutdown.take()}.")  // M, G and F1
        println(s"Closing ${proto1.nameToString()}...")
        proto1.close()
        println(s"Closing all Proto2 APs...")
        Ports.closeAllProto2APs()
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}


/* M */

case class Data_M() extends Session.Data

object M extends Actor("MyM") with Proto1.ActorM {

    def main(args: Array[String]): Unit =
        this.spawn(TestSieve.PORT_M)
        this.registerM(TestSieve.PORT_M, "localhost", TestSieve.PORT_Proto1, Data_M(), m1)

    def m1(d: Data_M, s: Proto1.M1): Done.type = s.sendStart().suspend(d, m2)

    def m2(d: Data_M, s: Proto1.M2): Done.type = s match {
        case Proto1.ExitM(sid, role, s) => finishAndClose(s)
    }

    override def afterClosed(): Unit = TestSieve.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestSieve.handleException(cause, addr, sid)
}


/* G */

case class Data_G() extends Session.Data

object G extends Actor("MyG") with Proto1.ActorG {

    private val NUM = 100

    def main(args: Array[String]): Unit =
        this.spawn(TestSieve.PORT_G)
        this.registerG(TestSieve.PORT_G, "localhost", TestSieve.PORT_Proto1, Data_G(), g1Suspend)

    def g1Suspend(d: Data_G, s: Proto1.G1Suspend): Done.type = s.suspend(d, g1)

    def g1(d: Data_G, s: Proto1.G1): Done.type = s match {
        case Proto1.StartG(sid, role, s) =>
            var s2 = s.sendNewPrime(2)
            var candidate: Int = 3
            while (candidate < NUM) {
                s2 = s2.sendLongBox(candidate)
                candidate = candidate + 2
            }
            s2.sendExit().suspend(d, g4)
    }

    def g4(d: Data_G, s: Proto1.G4): Done.type = s match {
        case Proto1.AckG(sid, role, s) =>
            val end = s.sendExit()
            finishAndClose(end)
    }

    /* Close */

    override def afterClosed(): Unit = TestSieve.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestSieve.handleException(cause, addr, sid)
}


/* F1 */

case class Data_F1() extends Session.Data {
    var x: Int = -1
    //var newPrime: Int = -1
    var f3: LinOption[Proto2.F3] = LinNone()
    var f13: LinOption[Proto1.F13] = LinNone()
}

object F1 extends Actor("MyF1") with Proto1.ActorF1 with Proto2.ActorF {

    val numMaxLocalPrimes = 3

    // TODO Data (per session) -- also factor out with F
    private val localPrimes = new Array[Long](numMaxLocalPrimes)
    private var availableLocalPrimes = 0

    // These two imply local is full
    private var hasNext: Boolean = false
    private val buff: collection.mutable.ListBuffer[Int] = collection.mutable.ListBuffer()  // Cache until Proto2 ready then send
    private var readyNext: Boolean = false

    private var pendingExit: Boolean = false  // Proto1 exited while Proto2 still being set up
    private var sentAck: Boolean = false
    private var receivedAck2: Boolean = false  // Only relevant if this.hasNext

    private def canClose: Boolean = this.sentAck && (!this.hasNext || this.receivedAck2)

    def main(args: Array[String]): Unit =
        this.spawn(TestSieve.PORT_F1)
        this.registerF1(TestSieve.PORT_F1, "localhost", TestSieve.PORT_Proto1, Data_F1(), f1_1Init)

    /* Proto1 */

    def f1_1Init(d: Data_F1, s: Proto1.F11Suspend): Done.type = s.suspend(d, f1_1)

    def f1_1(d: Data_F1, s: Proto1.F11): Done.type = s match {
        case Proto1.NewPrimeF1(sid, role, x, s) =>
            // save x in local primes
            this.localPrimes(this.availableLocalPrimes) = x  // i = 0
            this.availableLocalPrimes += 1
            s.suspend(d, f1_2)
    }

    def f1_2(d: Data_F1, s: Proto1.F12): Done.type = s match {
        case Proto1.ExitF1(sid, role, s) =>
            println(s"${nameToString()} received Exit: ${(0 until availableLocalPrimes).foldLeft("")((x, y) => x + localPrimes(y).toString + " ")}")
            val end = s.sendAck()
            this.sentAck = true
            if (!this.hasNext) {
                finishAndClose(end)
            } else {
                if (this.readyNext) {
                    d.f3 match {
                        case _: Session.LinNone => throw new RuntimeException("Missing frozen...")
                        case y: Session.LinSome[_] => ibecome(d, y, sendExit2)
                    }
                } else {
                    this.pendingExit = true  // Do sendExit2 later
                }
                end.finish()
            }
        case Proto1.LongBoxF1(sid, role, x, s) =>
            if (isLocallyPrime(x, localPrimes, 0, availableLocalPrimes)) {
                if (this.readyNext) {  // i.e., local already full
                    // become
                    d.f3 match {
                        case y: Session.LinSome[_] =>  // Proto2.F3
                            d.x = x
                            ibecome(d, y, f3LongBox)
                        case _: Session.LinNone => throw new RuntimeException("Missing frozen...")
                    }
                } else {  // !readyNext
                    if (storeLocally()) {
                        this.localPrimes(this.availableLocalPrimes) = x
                        this.availableLocalPrimes += 1
                    } else {
                        this.buff += x // TODO put in Data, cf. d.newPrime
                        if (!this.hasNext) {
                            val bport = Ports.spawnFreshProto2AP()
                            registerF(TestSieve.PORT_F1, "localhost", bport, d, f1Init)

                            val port = Ports.nextPort()
                            new F(s"F-$port", port, bport).main(Array()) // !!! FIXME _ in names not allowed
                            this.hasNext = true
                        }
                    }
                }
            }
            s.suspend(d, f1_2)
    }

    private def isLocallyPrime(candidate: Long, localPrimes: Array[Long], startInc: Int, endExc: Int): Boolean =
        !(startInc until endExc).exists(x => (candidate % localPrimes(x)) == 0)

    private def storeLocally(): Boolean = this.availableLocalPrimes < this.numMaxLocalPrimes

    /* Proto2 */

    def f1Init(d: Data_F1, s: Proto2.F1Suspend): Done.type = s.suspend(d, f1)

    def f1(d: Data_F1, s: Proto2.F1): Done.type = s match {
        case Proto2.ReadyF(sid, role, s) =>
            this.readyNext = true

            var s3 = s.sendNewPrime(this.buff.head)
            for (i <- 1 until this.buff.length) {
                s3 = s3.sendLongBox2(this.buff(i))
            }

            if (this.pendingExit) {
                sendExit2(d, s3)
            } else {
                val (a, done) = freeze(s3, (sid, r, a) => Proto2.F3(sid, r, a))
                d.f3 = a
                done
            }
    }

    // become
    private def f3LongBox(d: Data_F1, s: Proto2.F3): Done.type =
        val s1 = s.sendLongBox2(d.x)
        val (a, done) = freeze(s1, (sid, r, a) => Proto2.F3(sid, r, a))
        d.f3 = a
        done

    // become
    private def sendExit2(d: Data_F1, s: Proto2.F3): Done.type = s.sendExit2().suspend(d, f4)

    // Pre: hasNext => receive Ack2 from Fnext
    // Can enter via Proto1 Exit or Proto2 pendingExit
    def f4(d: Data_F1, s: Proto2.F4): Done.type = s match {
        case Proto2.Ack2F(sid, role, s) =>
            this.receivedAck2 = true
            if (canClose) {
                finishAndClose(s)
            } else {
                s.finish()
            }
    }

    /* Close */

    // Sent Ack to G and if hasNext then received Ack2 from Fnext
    override def afterClosed(): Unit = TestSieve.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestSieve.handleException(cause, addr, sid)
}


/* Ports */

object Ports {

    private val ports = AtomicInteger(3333)
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


/* F */

case class DataD() extends Session.Data {
    var x: Int = -1
    //var newPrime: Int = -1
    var f3: LinOption[Proto2.F3] = LinNone()
    var fn4: LinOption[Proto2.Fnext4] = LinNone()
}

class F(pid: Pid, port: Port, aport: Port) extends Actor(pid)
    with Proto2.ActorF with Proto2.ActorFnext {

    val numMaxLocalPrimes = 3

    private val localPrimes = new Array[Long](numMaxLocalPrimes)
    private var availableLocalPrimes = 0

    // These two imply local is full
    private var hasNext: Boolean = false
    private var readyNext: Boolean = false
    private val buff: collection.mutable.ListBuffer[Int] = collection.mutable.ListBuffer()  // Cache until Proto2 ready then send

    private var pendingExit: Boolean = false  // Proto1 exited while Proto2 still being set up
    private var sentAck: Boolean = false
    private var receivedAck2: Boolean = false  // Only relevant if this.hasNext

    def main(args: Array[String]): Unit = {
        this.spawn(port)
        this.registerFnext(port, "localhost", aport, DataD(), n1Init)
    }

    /* Proto2 Fnext */

    def n1Init(d: DataD, s: Proto2.Fnext1): Done.type = s.sendReady().suspend(d, n2)

    def n2(d: DataD, s: Proto2.Fnext2): Done.type = s match {
        case Proto2.NewPrimeFnext(sid, role, x, s) =>
            // save x in locals
            this.localPrimes(availableLocalPrimes) = x  // i = 0
            this.availableLocalPrimes += 1
            s.suspend(d, n3)
    }

    def n3(d: DataD, s: Proto2.Fnext3): Done.type = s match {
        case Proto2.Exit2Fnext(sid, role, s) =>
            val msg = (0 until availableLocalPrimes).foldLeft("")(
                (x, y) => x + localPrimes(y).toString + " ")
            println(s"${nameToString()} received Exit: $msg")
            if (!this.hasNext) {
                finishAndClose(s.sendAck2())  // No next, send Ack2 to prev now
            } else {
                // Send Ack2 to prev later after receiving Ack2 from next
                val (a, done) = freeze(s, (sid, r, a) => Proto2.Fnext4(sid, r, a))
                d.fn4 = a
                if (this.readyNext) {
                    d.f3 match {
                        case _: Session.LinNone => throw new RuntimeException("Missing frozen...")
                        case y: Session.LinSome[_] => ibecome(d, y, sendExit2ToNext)
                    }
                } else {
                    this.pendingExit = true  // Do sendExit2ToNext later
                }
                done
            }

        case Proto2.LongBox2Fnext(sid, role, x, s) =>
            if (isLocallyPrime(x, localPrimes, 0, availableLocalPrimes)) {
                if (readyNext) {
                    // become
                    d.f3 match {
                        case _: Session.LinNone => throw new RuntimeException("Missing frozen...")
                        case y: Session.LinSome[_] =>
                            d.x = x
                            ibecome(d, y, f3LongBox)
                    }
                } else {
                    if (storeLocally()) {
                        this.localPrimes(this.availableLocalPrimes) = x // i = 0
                        this.availableLocalPrimes = this.availableLocalPrimes + 1
                    } else {
                        this.buff += x
                        if (this.hasNext) {
                        } else {
                            val bport = Ports.spawnFreshProto2AP()
                            registerF(port, "localhost", bport, d, f1Init)

                            val nport = Ports.nextPort()
                            new F(s"F-$nport", nport, bport).main(Array()) // !!! FIXME _ in names not allowed
                            this.hasNext = true
                        }
                    }
                }
            }
            s.suspend(d, n3)
    }

    // become
    private def sendAck2ToPrev(d: DataD, s: Proto2.Fnext4): Done.type = s.sendAck2().finish()

    private def isLocallyPrime(candidate: Long, localPrimes: Array[Long], startInc: Int, endExc: Int): Boolean =
        !(startInc until endExc).exists(x => (candidate % localPrimes(x)) == 0)

    private def storeLocally(): Boolean = this.availableLocalPrimes < this.numMaxLocalPrimes

    /* Proto2 F */

    def f1Init(d: DataD, s: Proto2.F1Suspend): Done.type = s.suspend(d, f1)

    def f1(d: DataD, s: Proto2.F1): Done.type =
        s match {
            case Proto2.ReadyF(sid, role, s) =>
                this.readyNext = true

                var s3 = s.sendNewPrime(this.buff.head)
                for (i <- 1 until this.buff.length) {
                    s3 = s3.sendLongBox2(this.buff(i))
                }

                if (this.pendingExit) {
                    sendExit2ToNext(d, s3)
                } else {
                    val (a, done) = freeze(s3, (sid, r, a) => Proto2.F3(sid, r, a))
                    d.f3 = a
                    done
                }
        }

    // become
    private def f3LongBox(d: DataD, s: Proto2.F3): Done.type =
        val s1 = s.sendLongBox2(d.x)
        val (a, done) = freeze(s1, (sid, r, a) => Proto2.F3(sid, r, a))
        d.f3 = a
        done

    // become
    private def sendExit2ToNext(d: DataD, s: Proto2.F3): Done.type = s.sendExit2().suspend(d, f4)

    // Pre: this.hasNext => send Ack2 to prev after we receive Ack2 from next
    def f4(d: DataD, s: Proto2.F4): Done.type = s match {
        case Proto2.Ack2F(sid, role, s) =>
            d.fn4 match {
                case _: Session.LinNone => throw new RuntimeException("Missing frozen...")
                case y: Session.LinSome[_] => ibecome(d, y, sendAck2ToPrev)
            }
            finishAndClose(s)
    }

    /* Close */

    // Sent Ack2 to F and if hasNext then received Ack2 from Fnext
    override def afterClosed(): Unit =
        println(s"Closed ${this.pid}.")

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestSieve.handleException(cause, addr, sid)
}
