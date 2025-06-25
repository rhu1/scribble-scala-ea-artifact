package ea.example.lockid

import ea.example.lockid.LockId.Proto1
import ea.runtime.Net.{Pid, Port}
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue
import scala.util.Random

object TestLockId {

    val PORT_Proto1: Port = S.PORT_Proto1

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val proto1 = new Proto1.Proto1
        proto1.spawn(PORT_Proto1)

        Thread.sleep(500)

        //S.debug = true
        S.main(Array())

        val cs = collection.mutable.ListBuffer[C]()
        while (shutdown.isEmpty) {  // Non-terminating by default
            val port_C = Ports_C.nextPort()
            val c = new C(s"C-$port_C", port_C, PORT_Proto1)
            cs += c
            c.main(Array()) // !!! FIXME _ in names not allowed
            Thread.sleep(2000)
        }

        cs.foreach(_.enqueueClose())

        for i <- 1 to (cs.length + 1) do println(s"Closed ${shutdown.take()}.")  // S and Cs
        println(s"Closing ${proto1.nameToString()}...")
        proto1.close()
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}




/* S */

object Data_S extends Session.Data  {

    private var locked: Boolean = false
    private var x: Int = 0

    def get(): Int =
        x += 1
        x

    // returns true if lock obtained, else false
    def isLocked: Boolean = this.locked

    def lock(): Boolean =
        if (locked) {
            false
        } else {
            locked = true
            true
        }

    def unlock(): Unit = locked = false
}

object S extends Actor("MyS") with Proto1.ActorS {

    val PORT_Proto1 = 8888
    private val PORT_S = 7777

    def main(args: Array[String]): Unit =
        this.spawn(PORT_S)
        this.registerS(PORT_S, "localhost", PORT_Proto1, Data_S, s1Init)

    def s1Init(d: Data_S.type, s: Proto1.S1Suspend): Done.type =
        this.registerS(PORT_S, "localhost", PORT_Proto1, Data_S, s1Init)
        s.suspend(d, s1)

    def s1(d: Data_S.type, s: Proto1.S1): Done.type =
        s match {
            case Proto1.IDRequestS(sid, role, x, s) =>
                if (d.isLocked) {
                    s.sendReqUnavailable().suspend(d, s1)
                } else {
                    s.sendIDResponse(d.get()).suspend(d, s1)
                }
            case Proto1.LockRequestS(sud, role, s) =>
                if (d.isLocked) {
                    s.sendLockUnavailable().suspend(d, s1)
                } else {
                    d.lock()
                    s.sendLocked().suspend(d, s4)
                }
        }

    def s4(d: Data_S.type, s: Proto1.S4): Done.type = s match {
            case Proto1.UnlockS(sid, role, s) =>
                d.unlock()
                s.suspend(d, s1)
        }

    /* Close */

    override def afterClosed(): Unit = TestLockId.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestLockId.handleException(cause, addr, sid)
}


/* C */

object Ports_C {
    private var count = 4444

    def nextPort(): Int =
        count += 1
        count
}

case class Data_C() extends Session.Data

class C(pid_C: Pid, port_C: Port, port_Proto1: Port) extends Actor(pid_C) with Proto1.ActorC {

    def main(args: Array[String]): Unit =
        println(s"${nameToString()} Spawning $port_C ...")
        this.spawn(port_C)
        this.registerC(port_C, "localhost", port_Proto1, Data_C(), c1)

    private val rand = new Random()

    def c1(d: Data_C, s: Proto1.C1): Done.type =
        if (rand.nextInt(3) == 0) {
            s.sendLockRequest().suspend(d, c3)
        } else {
            s.sendIDRequest(nameToString()).suspend(d, c2)
        }

    def c2(d: Data_C, s: Proto1.C2): Done.type =
        s match {
            case Proto1.IDResponseC(sid, role, x, s) =>
                println(s"${nameToString()} Received Id: $x")
                Thread.sleep(1000)
                c1(d, s)
            case Proto1.ReqUnavailableC(sid, role, s) =>
                println(s"${nameToString()} ID request unavailable.")
                Thread.sleep(1000)
                c1(d, s)
        }

    def c3(d: Data_C, s: Proto1.C3): Done.type =
        s match {
            case Proto1.LockedC(sid, role, s) =>
                println(s"${nameToString()} Locked...")
                Thread.sleep((rand.nextInt(3)+1)*1000)
                println(s"${nameToString()} ...unlocking.")
                c1(d, s.sendUnlock())
            case Proto1.LockUnavailableC(sid, role, s) =>
                println(s"${nameToString()} Lock request unavailable.")
                c1(d, s)
        }

    /* Close */

    override def afterClosed(): Unit = TestLockId.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestLockId.handleException(cause, addr, sid)
}
