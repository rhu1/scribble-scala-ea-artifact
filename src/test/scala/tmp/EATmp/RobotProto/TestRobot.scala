package tmp.EATmp.RobotProto

import ea.runtime.{Actor, Done, Net, Session}

import java.net.SocketAddress

object TestRobot {

    def main(args: Array[String]): Unit = {
        println("Hello")

        val ap1 = new RobotProto()

        ap1.spawn(8888)
        Thread.sleep(1000)

        val door = new D("Door", 6666, "localhost", 8888)
        door.spawn()
        W.spawn()

        var i = 1
        while (true) {
            val r1 = new R(s"robot${i}", 7777+i)
            //r1.debug = true
            r1.spawn()
            Thread.sleep(1000)
            i += 1
        }

    }
}


/* ... */

class DataR(var i: Int) extends Session.Data

class R(pid: String, val port: Net.Port) extends Actor(pid) with ActorR {

    def spawn(): Unit = {
        spawn(this.port)
        registerR(this.port, "localhost", 8888, new DataR(0), r1)
    }

    def r1(d: DataR, s: R1): Done.type = {
        val partNum = s"partnum_"
        s.sendWantD(partNum).sendWantW(partNum).suspend(d, r3)
    }

    def r3(d: DataR, s: R3): Done.type = {
        s match {
            case BusyR(sid, x: String, s) =>
                println(s"${pid} Denied.")
                finishAndClose(s)
            case GoInR(sid, x: String, s) =>
                println(s"${pid} Entered...")
                s.sendInside("inside")
                    .suspend(d, r5)
        }
    }

    def r5(d: DataR, s: R5): Done.type = {
        s match {
            case DeliveredR(sid, x: String, s) =>
                Thread.sleep(3000)
                s.sendPartTaken("taken_")
                    .sendWantLeave("leave")
                    .suspend(d, r8)
        }
    }

    def r8(d: DataR, s: R8): Done.type = {
        s match {
            case GoOutR(sid, x: String, s) =>
                println(s"...${pid} exited.")
                finishAndClose(s.sendOutside("outside"))
        }
    }

    override def handleException(addr: SocketAddress): Unit = {
        print(s"Channel exception from: ${addr}")
    }
}


/* ... */

class DataD(var i: Int) extends Session.Data

class D(pid: Net.Pid, port: Net.Port, apHost: Net.Host, apPort: Net.Port) extends Actor(pid) with ActorD {

    private var isBusy = false

    def spawn(): Unit = {
        super.spawn(this.port)
        registerForInit(new DataD(0))
    }

    def registerForInit(d: DataD) = registerD(this.port, apHost, apPort, d, d1Suspend)

    def d1Suspend(d: DataD, s: D1Suspend): Done.type = { registerForInit(new DataD(0)); s.suspend(d, d1) }

    def d1(d: DataD, s: D1): Done.type = {
        s match {
            case WantDD(sid, x: String, s) =>
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

    def d5(d: DataD, s: D5): Done.type = {
        s match {
            case InsideD(sid, x: String, s) => s.suspend(d, d6)
        }
    }

    def d6(d: DataD, s: D6): Done.type = {
        s match {
            case PreparedD(sid, x: String, s) =>
                s.sendDeliver("deliver")
                    .suspend(d, d8)
        }
    }

    def d8(d: DataD, s: D8): Done.type = {
        s match {
            case WantLeaveD(sid, x: String, s) =>
                s.sendGoOut("exit")
                    .suspend(d, d10)
        }
    }

    def d10(d: DataD, s: D10): Done.type = {
        s match {
            case OutsideD(sid, x: String, s) =>
                this.isBusy = false
                println(s"${pid} not busy.")
                s.suspend(d, d11)
        }
    }

    def d11(d: DataD, s: D11): Done.type = {
        s match {
            case TableIdleD(sid, x: String, s) =>  // XXX may arrive before d10 fired, so TableIdleD handler not ready yet
                //finishAndClose(s)
                s.finish()
        }
    }

    override def handleException(addr: SocketAddress): Unit = {
        print(s"Channel exception from: ${addr}")
    }
}


/* ... */

class DataW(var i: Int) extends Session.Data

object W extends Actor("Warehouse") with ActorW {

    val port = 5555

    def spawn(): Unit = {
        spawn(this.port)
        registerW(this.port, "localhost", 8888, new DataW(0), w1Suspend)
    }

    def w1Suspend(d: DataW, s: W1Suspend): Done.type = {
        registerW(this.port, "localhost", 8888, new DataW(0), w1Suspend)
        s.suspend(d, w1)
    }

    def w1(d: DataW, s: W1): Done.type = {
        s match {
            case WantWW(sid, x: String, s) => s.suspend(d, w2)
        }
    }

    def w2(d: DataW, s: W2): Done.type = {
        s match {
            case CancelW(sid, x: String, s) =>
                //finishAndClose(s)
                s.finish()
            case PrepareW(sid, x: String, s) =>
                s.sendPrepared("ready").suspend(d, w4)
        }
    }

    def w4(d: DataW, s: W4): Done.type = {
        s match {
            case DeliverW(sid, x: String, s) =>
                s.sendDelivered("ready").suspend(d, w6)
        }
    }

    def w6(d: DataW, s: W6): Done.type = {
        s match {
            case PartTakenW(sid, x: String, s) =>
                //finishAndClose(s.sendTableIdle("idle"))
                s.sendTableIdle("idle").finish()
        }
    }

    override def handleException(addr: SocketAddress): Unit = {
        print(s"Channel exception from: ${addr}")
    }
}

