package ea.example.savina.dining

import ea.example.savina.dining.Dining.Proto1
import ea.example.savina.dining.Dining.Proto2
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Net, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.atomic.AtomicBoolean


object TestDining {

    val PORT_Proto1 = 8888
    val PORT_Proto2 = 9999
    val PORT_M = 7777
    val PORT_A = 6666

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val ap_Proto1 = new Proto1.Proto1
        ap_Proto1.spawn(PORT_Proto1)
        val ap_Proto2 = new Proto2.Proto2
        ap_Proto2.spawn(PORT_Proto2)

        Thread.sleep(500)

        val N = 5  // #philosophers
        val C = 3  // #courses
        val M = new M(N)
        val A = new A(N)

        //M.debug = true
        //A.debug = true
        val Ps = Array.tabulate[Phil](N)(i => {
            val loopActor = new Phil(i + 1, s"P-${i}", 4444+i, C)
            loopActor.main(Array())
            loopActor
        })
        A.main(Array())
        M.main(Array())

        for i <- 1 to 2 + N do println(s"Closed ${shutdown.take()}.")  // M, A and N philosophers
        println(s"Closing ${ap_Proto1.nameToString()}...")
        ap_Proto1.close()
        println(s"Closing ${ap_Proto2.nameToString()}...")
        ap_Proto2.close()
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

class M(val N: Int) extends Actor("MyM") with Proto1.ActorM {

    private var count = 0

    def main(args: Array[String]): Unit = {
        this.spawn(TestDining.PORT_M)
        // ...register and run for each P
        for (i <- Range.inclusive(1, N)) {
            this.registerM(TestDining.PORT_M, "localhost", TestDining.PORT_Proto1, Data_M(), m1)
        }
    }

    def m1(d: Data_M, s: Proto1.M1): Done.type = {
        val s2 = s.sendStart()
        this.count = this.count + 1
        if (this.count == this.N) {
            finishAndClose(s2)
        } else {
            s2.finish()
        }

    }

    override def afterClosed(): Unit = TestDining.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestDining.handleException(cause, addr, sid)
}


/* A */

case class Data_A() extends Session.Data

// numForks = numPhils = N
class A(val numForks: Int) extends Actor("MyA") with Proto2.ActorA {

    private val forks = Array.tabulate(numForks)(i => new AtomicBoolean(false))

    // Move to Data?
    private var numExitedPhilosophers = 0

    def main(args: Array[String]): Unit = {
        this.spawn(TestDining.PORT_A)
        for (i <- Range.inclusive(1, numForks)) {
            this.registerA(TestDining.PORT_A, "localhost", TestDining.PORT_Proto2, Data_A(), a1Init)
        }
    }

    def a1Init(d: Data_A, s: Proto2.A1Suspend): Done.type = s.suspend(d, a1)

    def a1(d: Data_A, s: Proto2.A1): Done.type = {
        //println(s"(${s.sid}) A sending L1...")
        s match {
            case Proto2.Hungry0A(sid, role, x, s) =>
                val leftFork = forks(x-1)  // !!! -1
                val rightFork = forks((x) % numForks)

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
        }
    }

    def a3(d: Data_A, s: Proto2.A3): Done.type = {
        s match {
            case Proto2.HungryDA(sid, role, x, s) =>
                val leftFork = forks(x-1)  // !!! -1
                val rightFork = forks((x) % numForks)

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
        }
    }

    def a4(d: Data_A, s: Proto2.A4): Done.type = {
        s match {
            case Proto2.DoneA(sid, role, x, s) =>
                val leftFork = forks(x-1)  // !!! -1
                val rightFork = forks((x) % numForks)
                leftFork.set(false)
                rightFork.set(false)
                println(s"A release Phil ${x}...")
                s.suspend(d, a5)
        }
    }

    def a5(d: Data_A, s: Proto2.A5): Done.type = {
        s match {
            case Proto2.HungryEA(sid, role, x, s) =>
                val leftFork = forks(x-1)  // !!! forks
                val rightFork = forks((x) % numForks)

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
            case Proto2.ExitA(sid, role, s) =>
                numExitedPhilosophers = numExitedPhilosophers + 1
                //Thread.sleep(500)
                if (numForks == numExitedPhilosophers) {
                    finishAndClose(s)
                } else {
                    s.finish()
                }
        }
    }

    override def afterClosed(): Unit = TestDining.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestDining.handleException(cause, addr, sid)
}


/* Philosopher */

case class Data_Phil() extends Session.Data

class Phil(val id: Int, pid: Net.Pid, val port: Net.Port, var rem: Int) extends Actor(s"P-${port}") with Proto2.ActorP with Proto1.ActorP1 {

    def main(args: Array[String]): Unit = {
        spawn(this.port)
        println(s"P ${this.id} spawned.")
        registerP1(this.port, "localhost", TestDining.PORT_Proto1, Data_Phil(), p11Init)
    }

    def p11Init(d: Data_Phil, s: Proto1.P11Suspend): Done.type = s.suspend(d, p11)

    def p11(d: Data_Phil, s: Proto1.P11): Done.type = {
        println(s"Phil ${id} starting...")
        s match {
            case Proto1.StartP1(sid, role, s) =>
                println(s"Phil ${id} started.")
                registerP(port, "localhost", TestDining.PORT_Proto2, Data_Phil(), p1)
                s.finish()
        }
    }

    def p1(d: Data_Phil, s: Proto2.P1): Done.type = {
        println(s"Phil ${id} hungry0.")
        s.sendHungry0(id).suspend(d, p2)
    }

    def p2(d: Data_Phil, s: Proto2.P2): Done.type = {
        s match {
            case Proto2.DeniedP(sid, role, s) =>
                println(s"Phil ${id} denied. hungryD")
                s.sendHungryD(id).suspend(d, p2)
            case Proto2.EatP(sid, role, s) =>
                rem = rem - 1
                val s5 = s.sendDone(id)
                println(s"Phil ${id} done eating -- remaining ${rem}.")
                if (rem <= 0) {
                    val end = s5.sendExit()
                    //Thread.sleep(500)
                    finishAndClose(end)
                } else {
                    println(s"Phil ${id} hungryE")
                    s5.sendHungryE(id).suspend(d, p2)
                }
        }
    }

    override def afterClosed(): Unit = TestDining.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestDining.handleException(cause, addr, sid)
}
