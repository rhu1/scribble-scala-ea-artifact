package ea.example.lockid

import ea.example.lockid.LockId.Proto1
import ea.runtime.Session.*
import ea.runtime.{Actor, Done, Net, Session}

import java.net.SocketAddress
import scala.util.Random

object TestId {

    val PORT_Proto1 = 8888
    val PORT_S = 7777
    val PORT_C = 6666

    def main(args: Array[String]): Unit = {
        val ap_Proto1 = new Proto1.Proto1
        ap_Proto1.spawn(PORT_Proto1)

        Thread.sleep(500)

        //S.debug = true
        //C.debug = true
        S.main(Array());
        while (true) {  // Non-terminating
            val port = Ports.nextPort()
            new C(s"C-${port}", port).main(Array()) // !!! FIXME _ in names not allowed
            Thread.sleep(2000)
        }
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* Ports */

// TODO consider distributed port allocation
object Ports {
    private var count = 4444;

    def nextPort(): Int =
        count += 1
        count
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

    def main(args: Array[String]): Unit = {
        this.spawn(TestId.PORT_S)
        this.registerS(TestId.PORT_S, "localhost", TestId.PORT_Proto1, Data_S, s1Init)
    }

    def s1Init(d: Data_S.type, s: Proto1.S1Suspend): Done.type =
        this.registerS(TestId.PORT_S, "localhost", TestId.PORT_Proto1, Data_S, s1Init)
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

    def s4(d: Data_S.type, s: Proto1.S4): Done.type =
        s match {
            case Proto1.UnlockS(sid, role, s) =>
                d.unlock()
                s.suspend(d, s1)
        }

    //override def afterClosed(): Unit = TestSieve.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestId.handleException(cause, addr, sid)
}


/* C */

case class Data_C() extends Session.Data

class C(pid: Net.Pid, port: Net.Port) extends Actor(pid) with Proto1.ActorC {

    def main(args: Array[String]): Unit = {
        this.spawn(port)
        println(s"${nameToString()} registering ${port} ...")
        this.registerC(port, "localhost", TestId.PORT_Proto1, Data_C(), c1)
    }

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
                println(s"${nameToString()} received Id: ${x}")
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
                println(s"${nameToString()} locked...")
                Thread.sleep((rand.nextInt(3)+1)*1000)
                println(s"${nameToString()} ...unlocking.")
                c1(d, s.sendUnlock())
            case Proto1.LockUnavailableC(sid, role, s) =>
                println(s"${nameToString()} Lock request unavailable.")
                c1(d, s)
        }

    //override def afterClosed(): Unit = shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestId.handleException(cause, addr, sid)
}
