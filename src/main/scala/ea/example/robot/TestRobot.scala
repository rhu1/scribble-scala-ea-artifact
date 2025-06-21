package ea.example.robot

import ea.runtime.{Actor, Done, Net, Session}
import ea.example.robot.Robot.Proto1

import java.net.SocketAddress

object TestRobot {

    val PORT_Proto1 = 8888
    val PORT_D = 6666
    val PORT_R = 7777

    def main(args: Array[String]): Unit = {
        println("Hello")

        val ap1 = new Proto1.Proto1()

        ap1.spawn(PORT_Proto1)
        Thread.sleep(1000)

        val door = new D("Door", PORT_D, "localhost", PORT_Proto1)
        door.spawn()
        W.spawn()

        var i = 1
        while (true) {
            val r1 = new R(s"robot${i}", PORT_R + i)
            //r1.debug = true
            r1.spawn()
            Thread.sleep(1000)
            i += 1
        }

    }
}


/* ... */

class DataR(var i: Int) extends Session.Data

class R(pid: String, val port: Net.Port) extends Actor(pid) with Proto1.ActorR {

    def spawn(): Unit = {
        spawn(this.port)
        registerR(this.port, "localhost", TestRobot.PORT_Proto1, new DataR(0), r1)
    }

    def r1(d: DataR, s: Proto1.R1): Done.type = {
        val partNum = s"partnum_"
        s.sendWantD(partNum).sendWantW(partNum).suspend(d, r3)
    }

    def r3(d: DataR, s: Proto1.R3): Done.type = {
        s match {
            case Proto1.BusyR(sid, role, x: String, s) =>
                println(s"${pid} Denied.")
                finishAndClose(s)
            case Proto1.GoInR(sid, role, x: String, s) =>
                println(s"${pid} Entered...")
                s.sendInside("inside")
                    .suspend(d, r5)
        }
    }

    def r5(d: DataR, s: Proto1.R5): Done.type = {
        s match {
            case Proto1.DeliveredR(sid, role, x: String, s) =>
                Thread.sleep(3000)
                s.sendPartTaken("taken_")
                    .sendWantLeave("leave")
                    .suspend(d, r8)
        }
    }

    def r8(d: DataR, s: Proto1.R8): Done.type = {
        s match {
            case Proto1.GoOutR(sid, role, x: String, s) =>
                println(s"...${pid} exited.")
                finishAndClose(s.sendOutside("outside"))
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

class DataD(var i: Int) extends Session.Data

class D(pid: Net.Pid, port: Net.Port, apHost: Net.Host, apPort: Net.Port) extends Actor(pid) with Proto1.ActorD {

    private var isBusy = false

    def spawn(): Unit = {
        super.spawn(this.port)
        registerForInit(new DataD(0))
    }

    def registerForInit(d: DataD) = registerD(this.port, apHost, apPort, d, d1Suspend)

    def d1Suspend(d: DataD, s: Proto1.D1Suspend): Done.type = { registerForInit(new DataD(0)); s.suspend(d, d1) }

    def d1(d: DataD, s: Proto1.D1): Done.type = {
        s match {
            case Proto1.WantDD(sid, role, x: String, s) =>
                if (this.isBusy) {
                    //finishAndClose(s.sendBusy("busy").sendCancel("cancel"))
                    s.sendBusy("busy").sendCancel("cancel").finish()
                } else {
                    this.isBusy = true
                    println(s"${pid} busy.")
                    val partNum = x
                    s.sendGoIn("enter").sendPrepare(partNum).suspend(d, d5)
                }
        }
    }

    def d5(d: DataD, s: Proto1.D5): Done.type = {
        s match {
            case Proto1.InsideD(sid, role, x: String, s) => s.suspend(d, d6)
        }
    }

    def d6(d: DataD, s: Proto1.D6): Done.type = {
        s match {
            case Proto1.PreparedD(sid, role, x: String, s) =>
                s.sendDeliver("deliver")
                    .suspend(d, d8)
        }
    }

    def d8(d: DataD, s: Proto1.D8): Done.type = {
        s match {
            case Proto1.WantLeaveD(sid, role, x: String, s) =>
                s.sendGoOut("exit")
                    .suspend(d, d10)
        }
    }

    def d10(d: DataD, s: Proto1.D10): Done.type = {
        s match {
            case Proto1.OutsideD(sid, role, x: String, s) =>
                this.isBusy = false
                println(s"${pid} not busy.")
                s.suspend(d, d11)
        }
    }

    def d11(d: DataD, s: Proto1.D11): Done.type = {
        s match {
            case Proto1.TableIdleD(sid, role, x: String, s) =>  // XXX may arrive before d10 fired, so TableIdleD handler not ready yet
                //finishAndClose(s)
                s.finish()
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

class DataW(var i: Int) extends Session.Data

object W extends Actor("Warehouse") with Proto1.ActorW {

    val port = 5555

    def spawn(): Unit = {
        spawn(this.port)
        registerW(this.port, "localhost", TestRobot.PORT_Proto1, new DataW(0), w1Suspend)
    }

    def w1Suspend(d: DataW, s: Proto1.W1Suspend): Done.type = {
        registerW(this.port, "localhost", TestRobot.PORT_Proto1, new DataW(0), w1Suspend)
        s.suspend(d, w1)
    }

    def w1(d: DataW, s: Proto1.W1): Done.type = {
        s match {
            case Proto1.WantWW(sid, role, x: String, s) => s.suspend(d, w2)
        }
    }

    def w2(d: DataW, s: Proto1.W2): Done.type = {
        s match {
            case Proto1.CancelW(sid, role, x: String, s) =>
                //finishAndClose(s)
                s.finish()
            case Proto1.PrepareW(sid, role, x: String, s) =>
                s.sendPrepared("ready").suspend(d, w4)
        }
    }

    def w4(d: DataW, s: Proto1.W4): Done.type = {
        s match {
            case Proto1.DeliverW(sid, role, x: String, s) =>
                s.sendDelivered("ready").suspend(d, w6)
        }
    }

    def w6(d: DataW, s: Proto1.W6): Done.type = {
        s match {
            case Proto1.PartTakenW(sid, role, x: String, s) =>
                //finishAndClose(s.sendTableIdle("idle"))
                s.sendTableIdle("idle").finish()
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}

