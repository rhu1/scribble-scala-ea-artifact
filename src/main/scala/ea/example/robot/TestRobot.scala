package ea.example.robot

import ea.runtime.{Actor, Done, Net, Session}
import ea.runtime.Net.{Host, Pid_C, Port}
import ea.example.robot.Robot.Proto1

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestRobot {

    val PORT_Proto1: Port = W.PORT_Proto1
    val PORT_D: Port = 6666
    val PORT_R: Port = 7777

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val proto1 = new Proto1.Proto1

        proto1.spawn(PORT_Proto1)
        Thread.sleep(1000)

        val door = new D("Door", PORT_D, "localhost", PORT_Proto1)
        door.spawn()
        W.spawn()

        var i = 1
        val rs = collection.mutable.ListBuffer[R]()
        while (shutdown.isEmpty) {  // Non-terminating by default
            val r1 = new R(s"robot$i", PORT_R + i)
            //r1.debug = true
            rs += r1
            r1.spawn()
            Thread.sleep(1000)
            i += 1
        }

        println(s"Closed ${shutdown.take()}.")  // W
        door.enqueueClose()
        rs.foreach(_.enqueueClose())

        println(s"Closed ${shutdown.take()}.")  // D
        println(s"Closing ${proto1.nameToString()}...")
        proto1.close()
    }
}


/* R */

class Data_R extends Session.Data {}

class R(pid_R: String, val port_R: Port) extends Actor(pid_R) with Proto1.ActorR {

    def spawn(): Unit =
        spawn(this.port_R)
        registerR(this.port_R, "localhost", TestRobot.PORT_Proto1, new Data_R(), r1)

    def r1(d: Data_R, s: Proto1.R1): Done.type =
        val partNum = s"partnum_"
        s.sendWantD(partNum).sendWantW(partNum).suspend(d, r3)

    def r3(d: Data_R, s: Proto1.R3): Done.type = s match {
        case Proto1.BusyR(sid, role, x: String, s) =>
            println(s"${pid_R} Denied.")
            finishAndClose(s)
        case Proto1.GoInR(sid, role, x: String, s) =>
            println(s"${pid_R} Entered...")
            s.sendInside("inside")
                .suspend(d, r5)
    }

    def r5(d: Data_R, s: Proto1.R5): Done.type = s match {
        case Proto1.DeliveredR(sid, role, x: String, s) =>
            Thread.sleep(3000)
            s.sendPartTaken("taken_")
                .sendWantLeave("leave")
                .suspend(d, r8)
    }

    def r8(d: Data_R, s: Proto1.R8): Done.type = s match {
        case Proto1.GoOutR(sid, role, x: String, s) =>
            println(s"...${pid_R} Exited.")
            finishAndClose(s.sendOutside("outside"))
    }

    //override def afterClosed(): Unit = TestRobot.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}


/* D */

class Data_D extends Session.Data {}

class D(pid_D: Pid_C, port_D: Port, host_Proto1: Host, port_Proto1: Port)
    extends Actor(pid_D) with Proto1.ActorD {

    private var isBusy = false

    def spawn(): Unit = {
        super.spawn(this.port_D)
        registerForInit(new Data_D())
    }

    def registerForInit(d: Data_D): Unit = registerD(this.port_D, host_Proto1, port_Proto1, d, d1Suspend)

    def d1Suspend(d: Data_D, s: Proto1.D1Suspend): Done.type = { registerForInit(d); s.suspend(d, d1) }

    def d1(d: Data_D, s: Proto1.D1): Done.type = s match {
        case Proto1.WantDD(sid, role, x: String, s) =>
            if (this.isBusy) {
                s.sendBusy("busy").sendCancel("cancel").finish()
            } else {
                this.isBusy = true
                println(s"${pid_D} Busy.")
                val partNum = x
                s.sendGoIn("enter").sendPrepare(partNum).suspend(d, d5)
            }
        }

    def d5(d: Data_D, s: Proto1.D5): Done.type = s match {
        case Proto1.InsideD(sid, role, x: String, s) => s.suspend(d, d6)
    }

    def d6(d: Data_D, s: Proto1.D6): Done.type = s match {
        case Proto1.PreparedD(sid, role, x: String, s) =>
            s.sendDeliver("deliver").suspend(d, d8)
    }

    def d8(d: Data_D, s: Proto1.D8): Done.type = s match {
        case Proto1.WantLeaveD(sid, role, x: String, s) =>
            s.sendGoOut("exit").suspend(d, d10)
    }

    def d10(d: Data_D, s: Proto1.D10): Done.type = s match {
        case Proto1.OutsideD(sid, role, x: String, s) =>
            this.isBusy = false
            println(s"$pid_D Not busy now.")
            s.suspend(d, d11)
    }

    def d11(d: Data_D, s: Proto1.D11): Done.type = s match {
        case Proto1.TableIdleD(sid, role, x: String, s) => s.finish()
    }

    override def afterClosed(): Unit = TestRobot.shutdown.add(this.pid_D)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
}


/* W */

class Data_W extends Session.Data {}

object W extends Actor("Warehouse") with Proto1.ActorW {

    val PORT_Proto1 = 8888
    val port_W: Port = 5555

    def spawn(): Unit =
        spawn(this.port_W)
        registerW(this.port_W, "localhost", PORT_Proto1, new Data_W(), w1Suspend)

    def w1Suspend(d: Data_W, s: Proto1.W1Suspend): Done.type =
        registerW(this.port_W, "localhost", PORT_Proto1, d, w1Suspend)
        s.suspend(d, w1)

    def w1(d: Data_W, s: Proto1.W1): Done.type = s match {
        case Proto1.WantWW(sid, role, x: String, s) => s.suspend(d, w2)
    }

    def w2(d: Data_W, s: Proto1.W2): Done.type = s match {
        case Proto1.CancelW(sid, role, x: String, s) =>
            s.finish()
        case Proto1.PrepareW(sid, role, x: String, s) =>
            s.sendPrepared("ready").suspend(d, w4)
    }

    def w4(d: Data_W, s: Proto1.W4): Done.type = s match {
        case Proto1.DeliverW(sid, role, x: String, s) =>
            s.sendDelivered("ready").suspend(d, w6)
    }

    def w6(d: Data_W, s: Proto1.W6): Done.type = s match {
        case Proto1.PartTakenW(sid, role, x: String, s) =>
            //finishAndClose(s.sendTableIdle("idle"))
            s.sendTableIdle("idle").finish()
    }

    override def afterClosed(): Unit = TestRobot.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}

