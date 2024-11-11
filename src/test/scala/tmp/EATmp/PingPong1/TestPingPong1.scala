package tmp.EATmp.PingPong1

import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestPingPong1 {

    val c: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        println("Hello")

        val p1 = new PingPong1
        p1.debug = true
        p1.spawn(8888)
        Thread.sleep(1000)

        C.debug = true
        Pinger.debug = true
        PongReceiver.debug = true
        Ponger.debug = true

        PongReceiver.spawn()
        Ponger.spawn()
        Pinger.spawn()
        C.spawn()

        println(s"1: ${c.take()}")
        println(s"2: ${c.take()}")
        println(s"3: ${c.take()}")
        println(s"4: ${c.take()}")
        p1.close()
        Thread.sleep(500)
    }
}


case class DataA() extends Session.Data
case class DataB(var x: Int) extends Session.Data
case class DataC() extends Session.Data
case class DataD() extends Session.Data


/* ... */

object C extends Actor("MyC") with ActorC {

    def spawn(): Unit = {
        spawn(5555)
        registerC(5555, "localhost", 8888, DataA(), c1)
    }

    def c1(d: DataA, s: C1): Done.type = {
        s.sendStart().suspend(d, c2)
    }

    def c2(d: DataA, s: C2): Done.type = {
        s match {
            case StopC(sid, role, s) =>
                TestPingPong1.c.add("C")  // finishAndClose can throw IOException
                Thread.sleep(500)
                val done = finishAndClose(s)
                done
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        TestPingPong1.c.add("C")

        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
    }
}


/* ... */

object Pinger extends Actor("MyPinger") with ActorPinger {

    def spawn(): Unit = {
        spawn(6666)
        registerPinger(6666, "localhost", 8888, DataC(), pingerInit)
    }

    def pingerInit(d: DataC, s: Pinger1Suspend): Done.type = {
        s.suspend(d, pinger1)
    }

    def pinger1(d: DataC, s: Pinger1): Done.type = {
        s match {
            case StartPinger(sid, role, s) =>
                s.sendPing().suspend(d, pinger3)
        }
    }

    def pinger3(d: DataC, s: Pinger3): Done.type = {
        s match {
            case PingCPinger(sid, role, s) =>
                s.sendPing().suspend(d, pinger3)
            case StopPinger(sid, role, s) =>
                val f = s.sendStop().sendStop()

                println("\nSTOP\n")

                TestPingPong1.c.add("Pinger")
                Thread.sleep(500)
                finishAndClose(f)
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        TestPingPong1.c.add("Pinger")

        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
    }
}


/* ... */

object PongReceiver extends Actor("MyPongReceiver") with ActorPongReceiver {

    def spawn(): Unit = {
        spawn(7777)
        registerPongReceiver(7777, "localhost", 8888, DataB(2), pongReceiverInit)
    }

    def pongReceiverInit(d: DataB, s: PongReceiver1Suspend): Done.type = {
        s.suspend(d, pongReceiver1)
    }

    def pongReceiver1(d: DataB, s: PongReceiver1): Done.type = {
        s match {
            case PongPongReceiver(sid, role, s) =>
                if (d.x <= 0) {
                    val f = s.sendStop()
                    TestPingPong1.c.add("PongReceiver")
                    Thread.sleep(500)
                    finishAndClose(f)
                } else {
                    println("\nPING\n")
                    d.x = d.x - 1
                    s.sendPingC().suspend(d, pongReceiver1)
                }
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        TestPingPong1.c.add("PongReceiver")

        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
    }
}


/* ... */

object Ponger extends Actor("MyPonger") with ActorPonger {

    def spawn(): Unit = {
        spawn(9999)
        registerPonger(9999, "localhost", 8888, DataD(), pongerInit)
    }

    def pongerInit(d: DataD, s: Ponger1Suspend): Done.type = {
        s.suspend(d, ponger1)
    }

    def ponger1(d: DataD, s: Ponger1): Done.type = {

        println(s"\nPonger 1 ${s}\n")

        s match {
            case PingPonger(sid, role, s) =>
                s.sendPong().suspend(d, ponger3)
        }
    }

    def ponger3(d: DataD, s: Ponger3): Done.type = {

        println(s"\nPonger 3 ${s}\n")

        s match {
            case PingPonger3(sid, role, s) =>  // !!! PingPonger3 -- not PingPonger
                s.sendPong().suspend(d, ponger3)
            case StopPonger(sid, role, s) =>
                TestPingPong1.c.add("Ponger")
                Thread.sleep(500)
                finishAndClose(s)
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        //TestPingPong1.c.add("Ponger")

        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()

        //enqueueClose()
    }
}
