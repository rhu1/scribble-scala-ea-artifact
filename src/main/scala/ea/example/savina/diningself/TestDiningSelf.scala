package ea.example.savina.diningself

import ea.example.savina.diningself.DiningSelf.{Proto1, Proto2, Proto3}
import ea.runtime.Net.{Pid, Port}
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.atomic.AtomicBoolean


object TestDiningSelf {

    val PORT_Proto1: Port = 8888
    val PORT_Proto2: Port = 9999
    val PORT_M: Port = 7777
    val PORT_A: Port = 6666

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit =
        val ap_Proto1 = new Proto1.Proto1
        ap_Proto1.spawn(PORT_Proto1)
        val ap_Proto2 = new Proto2.Proto2
        ap_Proto2.spawn(PORT_Proto2)

        Thread.sleep(500)

        val N = 5  // #philosophers
        val C = 3  // #courses
        val M = new M(N, PORT_M, PORT_Proto1)
        val A = new A(N, PORT_A, PORT_Proto2)

        //M.debug = true
        //A.debug = true
        val Ps = Array.tabulate[Phil](N)(i => {
            val loopActor = new Phil(i + 1, s"P-$i", 4444+i, PORT_Proto1, PORT_Proto2, C)
            loopActor.main(Array())
            loopActor
        })
        A.main(Array())
        M.main(Array())

        for i <- 1 to 2 + N do println(s"Closed ${shutdown.take()}.")  // M, A and N philosophers
        println(s"Closing ${ap_Proto1.nameToString}...")
        ap_Proto1.close()
        println(s"Closing ${ap_Proto2.nameToString}...")
        ap_Proto2.close()

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}


/* M */

case class Data_M() extends Session.Data

class M(val N: Int, val port_M: Port, val port_Proto1: Port)
    extends Actor("MyM") with Proto1.ActorM {

    private var count = 0

    def main(args: Array[String]): Unit =
        this.spawn(this.port_M)
        // ...register and run for each P
        for (i <- Range.inclusive(1, N)) {
            this.registerM(this.port_M, "localhost", this.port_Proto1, Data_M(), m1)
        }

    def m1(d: Data_M, s: Proto1.M1): Done.type =
        val s2 = s.sendStart()
        this.count = this.count + 1
        if (this.count == this.N) {
            finishAndClose(s2)
        } else {
            s2.finish()
        }

    /* Close */

    override def afterClosed(): Unit = TestDiningSelf.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestDiningSelf.handleException(cause, addr, sid)
}


/* A */

case class Data_A() extends Session.Data

// numForks = numPhils = N
class A(val numForks: Int, val port_A: Port, val port_Proto2: Port)
    extends Actor("MyA") with Proto2.ActorA {

    private val forks = Array.tabulate(numForks)(i => new AtomicBoolean(false))

    // Move to Data?
    private var numExitedPhilosophers = 0

    def main(args: Array[String]): Unit =
        this.spawn(this.port_A)
        for (i <- Range.inclusive(1, numForks)) {
            this.registerA(this.port_A, "localhost", this.port_Proto2, Data_A(), a1Init)
        }

    def a1Init(d: Data_A, s: Proto2.A1Suspend): Done.type = s.suspend(d, a1)

    def a1(d: Data_A, s: Proto2.A1): Done.type = s match {
        case Proto2.Hungry0A(sid, role, x, s) =>
            val leftFork = forks(x-1)  // !!! -1
            val rightFork = forks(x % numForks)

            if (leftFork.get() || rightFork.get()) {
                // someone else has access to the fork
                println(s"A denying0 Phil $x...")
                s.sendDenied().suspend(d, a3)
            } else {
                println(s"A ok0 Phil $x...")
                leftFork.set(true)
                rightFork.set(true)
                s.sendEat().suspend(d, a4)
            }
    }

    def a3(d: Data_A, s: Proto2.A3): Done.type = s match {
        case Proto2.HungryDA(sid, role, x, s) =>
            val leftFork = forks(x-1)  // !!! -1
            val rightFork = forks(x % numForks)

            if (leftFork.get() || rightFork.get()) {
                // someone else has access to the fork
                println(s"A denyingD Phil $x...")
                s.sendDenied().suspend(d, a3)
            } else {
                leftFork.set(true)
                rightFork.set(true)
                println(s"A okD Phil $x...")
                s.sendEat().suspend(d, a4)
            }
    }

    def a4(d: Data_A, s: Proto2.A4): Done.type = s match {
        case Proto2.DoneA(sid, role, x, s) =>
            val leftFork = forks(x-1)  // !!! -1
            val rightFork = forks(x % numForks)
            leftFork.set(false)
            rightFork.set(false)
            println(s"A release Phil $x...")
            s.suspend(d, a5)
    }

    def a5(d: Data_A, s: Proto2.A5): Done.type = s match {
        case Proto2.HungryEA(sid, role, x, s) =>
            val leftFork = forks(x-1)  // !!! forks
            val rightFork = forks(x % numForks)

            if (leftFork.get() || rightFork.get()) {
                // someone else has access to the fork
                println(s"A denyingE Phil $x...")
                s.sendDenied().suspend(d, a3)
            } else {
                leftFork.set(true)
                rightFork.set(true)
                println(s"A okE Phil $x...")
                s.sendEat().suspend(d, a4)
            }
        case Proto2.ExitA(sid, role, s) =>
            numExitedPhilosophers = numExitedPhilosophers + 1
            if (numForks == numExitedPhilosophers) {
                finishAndClose(s)
            } else {
                s.finish()
            }
    }

    /* Close */

    override def afterClosed(): Unit = TestDiningSelf.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestDiningSelf.handleException(cause, addr, sid)
}


/* Philosopher */

object PhilPorts {
    private var apCounter = 3333

    def nextSelfPort(): Int = {
        this.apCounter += 1
        apCounter
    }
}

case class Data_Phil() extends Session.Data {
    var s1_1: Session.LinOption[Proto3.S11] = Session.LinNone()
    var p5: Session.LinOption[Proto2.P5] = Session.LinNone()
}

class Phil(val id: Int, pid_P: Pid, val port_P: Port, val port_Proto1: Port, val port_Proto2: Port, var rem: Int)
    extends Actor(s"P-$port_P")
    with Proto2.ActorP with Proto1.ActorP1 with Proto3.ActorS1 with Proto3.ActorS2 {

    private var proto3: Option[Proto3.Proto3] = None

    def main(args: Array[String]): Unit = {

        val port_self = PhilPorts.nextSelfPort()
        proto3 = Some(new Proto3.Proto3)
        proto3.foreach(_.spawn(port_self))

        Thread.sleep(100)

        spawn(this.port_P)
        println(s"Phil ${this.id} spawned.")

        val d = Data_Phil()
        registerS1(this.port_P, "localhost", port_self, d, s1_1)
        registerS2(this.port_P, "localhost", port_self, d, s2_1Init)
    }

    /* Proto3 */

    def s1_1(d: Data_Phil, s: Proto3.S11): Done.type =
        val (a, done) = Session.freeze(s,
            (sid: Session.Sid, role: Session.Role, a: Actor) => Proto3.S11(sid, role, a))
        d.s1_1 = a

        registerP1(this.port_P, "localhost", this.port_Proto1, d, p1_1Init)
        done

    private def s1Start(d: Data_Phil, s: Proto3.S11): Done.type =
        val s1 = s.sendSelfStart()
        val (a, done) = Session.freeze(s1,
            (sid: Session.Sid, role: Session.Role, a: Actor) => Proto3.S11(sid, role, a))
        d.s1_1 = a
        done

    private def s1Exit(d: Data_Phil, s: Proto3.S11): Done.type =
        val s1 = s.sendSelfExit()
        Done

    /* Proto 2 */

    def s2_1Init(d: Data_Phil, s: Proto3.S21Suspend): Done.type = s.suspend(d, s2_1)

    def s2_1(d: Data_Phil, s: Proto3.S21): Done.type = s match {
        case Proto3.SelfStartS2(sid, role, s) =>
            d.p5 match {
                case y: Session.LinSome[_] => Session.ibecome(d, y, p5Hungry)
                case _: Session.LinNone => throw new RuntimeException("Missing frozen...")
            }
            s.suspend(d, s2_1)
        case Proto3.SelfExitS2(sid, role, s) =>
            s.finish()
    }

    private def p5Hungry(d: Data_Phil, s: Proto2.P5): Done.type =
        s.sendHungryE(id).suspend(d, p2)

    /* Proto 1 */

    def p1_1Init(d: Data_Phil, s: Proto1.P11Suspend): Done.type = s.suspend(d, p1_1)

    def p1_1(d: Data_Phil, s: Proto1.P11): Done.type =
        println(s"Phil $id starting...")
        s match {
            case Proto1.StartP1(sid, role, s) =>
                println(s"Phil $id started.")
                registerP(port_P, "localhost", this.port_Proto2, d, p1)
                s.finish()
        }

    /* Proto2 */

    def p1(d: Data_Phil, s: Proto2.P1): Done.type =
        println(s"Phil $id hungry0.")
        s.sendHungry0(id).suspend(d, p2)

    def p2(d: Data_Phil, s: Proto2.P2): Done.type = s match {
        case Proto2.DeniedP(sid, role, s) =>
            println(s"Phil $id denied. hungryD")
            s.sendHungryD(id).suspend(d, p2)
        case Proto2.EatP(sid, role, s) =>
            rem = rem - 1
            val s5 = s.sendDone(id)
            println(s"Phil $id done eating -- remaining $rem.")
            if (rem <= 0) {
                val end = s5.sendExit()
                d.s1_1 match {
                    case y: Session.LinSome[_] => Session.ibecome(d, y, s1Exit)
                    case _: Session.LinNone => throw new RuntimeException("Critical error s1Exit...")
                }
                finishAndClose(end)
            } else {
                println(s"Phil $id hungryE")
                val (a, done) = Session.freeze(s5,
                    (sid: Session.Sid, role: Session.Role, a: Actor) => Proto2.P5(sid, role, a))
                d.p5 = a

                d.s1_1 match {
                    case _: Session.LinNone => throw new RuntimeException("Missing frozen...")
                    case y: Session.LinSome[_] => Session.ibecome(d, y, s1Start)
                }
            }
    }

    /* Close */

    override def afterClosed(): Unit =
        proto3.foreach(_.close())
        TestDiningSelf.shutdown.add(this.pid_P)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestDiningSelf.handleException(cause, addr, sid)
}
