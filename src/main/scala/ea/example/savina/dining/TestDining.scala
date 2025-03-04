package ea.example.savina.dining

import ea.example.savina.dining.Dining.Proto1.Proto1
import ea.example.savina.dining.Dining.Proto2.Proto2
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Net, Session}

import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicBoolean

object TestDining {
    
    val PORT_Proto1 = 8888
    val PORT_Proto2 = 9999
    val PORT_M = 7777
    val PORT_A = 6666

    def main(args: Array[String]): Unit = {
        val ap_Proto1 = new Proto1
        ap_Proto1.spawn(PORT_Proto1)
        val ap_Proto2 = new Proto2
        ap_Proto2.spawn(PORT_Proto2)
        
        Thread.sleep(500)

        val xN = 5
        val xM = 3
        val M = new M(xN)
        val A = new A(xN)

        //M.debug = true
        //A.debug = true
        M.main(Array())
        A.main(Array())

        val Ps = Array.tabulate[F](xN)(i => {
            val loopActor = new F(i + 1, s"P-${i}", 4444+i, xM)
            loopActor.main(Array())
            loopActor
        })
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* M */

case class Data_M() extends Session.Data

class M(val N: Int) extends Actor("MyM") with Dining.Proto1.ActorM {

    private var count = 0

    def main(args: Array[String]): Unit = {
        this.spawn(TestDining.PORT_M)
        // ...register and run for each P
        for (i <- Range.inclusive(1, N)) {
            this.registerM(TestDining.PORT_M, "localhost", TestDining.PORT_Proto1, Data_M(), m1)
            Thread.sleep(200)
        }
    }

    def m1(d: Data_M, s: Dining.Proto1.M1): Done.type = {
        val s2 = s.sendStart()
        this.count = this.count + 1
        if (this.count == this.N) {
            //Thread.sleep(500)
            finishAndClose(s2)
        } else {
            s2.finish()
        }

    }

    //override def afterClosed(): Unit = TestDining.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestDining.handleException(cause, addr, sid)
}


/* A */

case class DataA() extends Session.Data

// numForks = numPhils = N
class A(val numForks: Int) extends Actor("MyA") with Dining.Proto2.ActorA {

    // Move to Data?
    private val forks = Array.tabulate(numForks)(i => new AtomicBoolean(false))
    private var numExitedPhilosophers = 0

    def main(args: Array[String]): Unit = {
        this.spawn(TestDining.PORT_A)

        for (i <- Range.inclusive(1, numForks)) {
            this.registerA(TestDining.PORT_A, "localhost", TestDining.PORT_Proto2, DataA(), a1Init)
        }
    }

    def a1Init(d: DataA, s: Dining.Proto2.A1Suspend): Done.type = s.suspend(d, a1)

    def a1(d: DataA, s: Dining.Proto2.A1): Done.type = {
        //println(s"(${s.sid}) A sending L1...")
        s match {
            case Dining.Proto2.Hungry0A(sid, role, x, s) =>
                val leftFork = forks(x-1)  // !!! -1
                val rightFork = forks((x) % numForks)

                val done =
                if (leftFork.get() || rightFork.get()) {
                    // someone else has access to the fork
                    println(s"A denying0 Phil ${x}...")
                    s.sendDenied().suspend(d, a3)
                } else {
                    println(s"A ok0 Phil ${x}...")
                    leftFork.set(true)
                    rightFork.set(true)
                    s.sendEat().suspend(d, a4)
                }
                done
        }
    }

    def a3(d: DataA, s: Dining.Proto2.A3): Done.type = {
        s match {
            case Dining.Proto2.HungryDA(sid, role, x, s) =>
                val leftFork = forks(x-1)  // !!! -1
                val rightFork = forks((x) % numForks)

                val done =
                    if (leftFork.get() || rightFork.get()) {
                        // someone else has access to the fork
                        println(s"A denyingD Phil ${x}...")
                        s.sendDenied().suspend(d, a3)
                    } else {
                        leftFork.set(true)
                        rightFork.set(true)
                        println(s"A okD Phil ${x}...")
                        s.sendEat().suspend(d, a4)
                    }
                done
        }
    }

    def a4(d: DataA, s: Dining.Proto2.A4): Done.type = {
        s match {
            case Dining.Proto2.DoneA(sid, role, x, s) =>
                val leftFork = forks(x-1)  // !!! -1
                val rightFork = forks((x) % numForks)
                leftFork.set(false)
                rightFork.set(false)
                println(s"A release Phil ${x}...")
                s.suspend(d, a5)
        }
    }

    def a5(d: DataA, s: Dining.Proto2.A5): Done.type = {
        s match {
            case Dining.Proto2.HungryEA(sid, role, x, s) =>
                val leftFork = forks(x-1)  // !!! forks
                val rightFork = forks((x) % numForks)

                val done =
                    if (leftFork.get() || rightFork.get()) {
                        // someone else has access to the fork
                        println(s"A denyingE Phil ${x}...")
                        s.sendDenied().suspend(d, a3)
                    } else {
                        leftFork.set(true)
                        rightFork.set(true)
                        println(s"A okE Phil ${x}...")
                        s.sendEat().suspend(d, a4)
                    }
                done
            case Dining.Proto2.ExitA(sid, role, s) =>
                numExitedPhilosophers = numExitedPhilosophers + 1
                Thread.sleep(500)
                if (numForks == numExitedPhilosophers) {
                    finishAndClose(s)
                } else {
                    s.finish()
                }
        }
    }
    
    //override def afterClosed(): Unit = TestDining.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestDining.handleException(cause, addr, sid)
}


/* B */

case class DataB() extends Session.Data

class F(val id: Int, pid: Net.Pid, val port: Net.Port, var rem: Int) extends Actor(s"P-${port}") with Dining.Proto2.ActorP with Dining.Proto1.ActorP1 {

    def main(args: Array[String]): Unit = {
        spawn(port)
        println(s"P ${id} spawned.")
        registerP1(port, "localhost", 8888, DataB(), p11Init)  // !!! mutable data
    }

    def p11Init(d: DataB, s: Dining.Proto1.P11Suspend): Done.type = {
        s.suspend(d, p11)
    }

    def p11(d: DataB, s: Dining.Proto1.P11): Done.type = {
        println(s"Phil ${id} starting...")
        s match {
            case Dining.Proto1.StartP1(sid, role, s) =>
                println(s"Phil ${id} started.")
                registerP(port, "localhost", 9999, DataB(), p1)
                s.finish()
        }
    }

    def p1(d: DataB, s: Dining.Proto2.P1): Done.type = {
        println(s"Phil ${id} hungry0.")
        s.sendHungry0(id).suspend(d, p2)
    }

    def p2(d: DataB, s: Dining.Proto2.P2): Done.type = {
        s match {
            case Dining.Proto2.DeniedP(sid, role, s) =>
                println(s"Phil ${id} denied. hungryD")
                s.sendHungryD(id).suspend(d, p2)
            case Dining.Proto2.EatP(sid, role, s) =>
                rem = rem - 1
                val s5 = s.sendDone(id)
                println(s"Phil ${id} done eating -- remaining ${rem}.")
                if (rem <= 0) {
                    val end = s5.sendExit()
                    Thread.sleep(500)
                    finishAndClose(end)
                } else {
                    println(s"Phil ${id} hungryE")
                    s5.sendHungryE(id).suspend(d, p2)
                }
        }
    }

    //override def afterClosed(): Unit = TestDining.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestDining.handleException(cause, addr, sid)
}
