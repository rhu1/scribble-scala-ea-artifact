package ea.example.savina.ping

import ea.example.savina.ping.Ping.Proto1.*
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestPing {

    val PORT_Proto1 = 8888
    val PORT_C = 5555;
    val PORT_Pinger = 6666;
    val PORT_PongReceiver = 7777;
    val PORT_Ponger = 9999;

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val ap_Proto1 = new Proto1
        //ap_Proto1.debug = true
        ap_Proto1.spawn(PORT_Proto1)

        Thread.sleep(500)

        //C.debug = true
        //Pinger.debug = true
        //PongReceiver.debug = true
        //Ponger.debug = true
        Ponger.spawn()
        PongReceiver.spawn()
        Pinger.spawn()
        C.spawn()

        for i <- 1 to 4 do println(s"Closed ${shutdown.take()}.")
        println(s"Closing ${ap_Proto1.nameToString()}...")
        ap_Proto1.close()
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
    }
}


/* C */

case class Data_C() extends Session.Data

object C extends Actor("MyC") with ActorC {

    def spawn(): Unit = {
        this.spawn(TestPing.PORT_C)
        this.registerC(TestPing.PORT_C, "localhost", TestPing.PORT_Proto1, Data_C(), c1)
    }

    def c1(d: Data_C, s: C1): Done.type = s.sendStart().suspend(d, c2)

    def c2(d: Data_C, s: C2): Done.type =
        s match {
            case StopC(sid, role, s) => this.finishAndClose(s)
        }

    override def afterClosed(): Unit = TestPing.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestPing.handleException(cause, addr, sid)
}


/* Pinger */

case class Data_Pinger() extends Session.Data

object Pinger extends Actor("MyPinger") with ActorPinger {

    def spawn(): Unit = {
        this.spawn(TestPing.PORT_Pinger)
        this.registerPinger(TestPing.PORT_Pinger, "localhost", TestPing.PORT_Proto1, Data_Pinger(), pingerInit)
    }

    def pingerInit(d: Data_Pinger, s: Pinger1Suspend): Done.type = s.suspend(d, pinger1)

    def pinger1(d: Data_Pinger, s: Pinger1): Done.type =
        s match {
            case StartPinger(sid, role, s) =>
                println(s"${nameToString()} received Start")
                s.sendPing0().suspend(d, pinger3)
        }

    def pinger3(d: Data_Pinger, s: Pinger3): Done.type =
        s match {
            case PingCPinger(sid, role, s) =>
                println(s"${nameToString()} received PingC")
                s.sendPing().suspend(d, pinger3)
            case StopPinger(sid, role, s) =>
                val end = s.sendStop().sendStop()
                //println("\nSTOP\n")
                this.finishAndClose(end)
        }

    override def afterClosed(): Unit = TestPing.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestPing.handleException(cause, addr, sid)
}


/* PongReceiver */

case class Data_Receiver(var rem: Int) extends Session.Data

object PongReceiver extends Actor("MyPongReceiver") with ActorPongReceiver {

    val REPEATS = 2

    def spawn(): Unit = {
        this.spawn(TestPing.PORT_PongReceiver)
        this.registerPongReceiver(TestPing.PORT_PongReceiver, "localhost",
            TestPing.PORT_Proto1, Data_Receiver(REPEATS), pongReceiverInit)
    }

    def pongReceiverInit(d: Data_Receiver, s: PongReceiver1Suspend): Done.type =
        s.suspend(d, pongReceiver1or3)

    sealed trait PongReceiver1or3[T] {
        def print(s: T): Unit =
            s match { // No longer exhaustively checked?
                case Pong0PongReceiver(sid, role, s) => println(s"${nameToString()} received Pong0")
                case PongPongReceiver(sid, role, s) => println(s"${nameToString()} received Pong")
            }

        def sendPingC(a: Actor, d: Data_Receiver, s: T): Done.type =
            s match {
                case Pong0PongReceiver(sid, role, s) => s.sendPingC().suspend(d, pongReceiver1or3)
                case PongPongReceiver(sid, role, s) => s.sendPingC().suspend(d, pongReceiver1or3)
            }

        def stop(a: Actor, d: Data_Receiver, s: T): Done.type =
            s match {
                case Pong0PongReceiver(sid, role, s) => a.finishAndClose(s.sendStop())
                case PongPongReceiver(sid, role, s) => a.finishAndClose(s.sendStop())
            }
    }

    object PongReceiver1or3 {
        implicit val T_1: PongReceiver1or3[PongReceiver1] = new PongReceiver1or3[PongReceiver1] {}
        implicit val T_3: PongReceiver1or3[PongReceiver3] = new PongReceiver1or3[PongReceiver3] {}
    }

    //def pongReceiver1or3[T: PongReceiver1or3](d: Data_Receiver, s: T): Done.type = {
    def pongReceiver1or3[T](d: Data_Receiver, s: T)(implicit ev: PongReceiver1or3[T]): Done.type = {
        ev.print(s)
        if (d.rem <= 0) {
            //stop(d, s)
            ev.stop(this, d, s)
        } else {
            d.rem = d.rem - 1
            println(s"${nameToString()} sending PingC, remaining ${d.rem}...")
            //sendPingC(d, s)
            ev.sendPingC(this, d, s)
        }
    }

    /*def stop[T: PongReceiver1or3](d: Data_Receiver, s: T): Done.type =
        s match {
            case Pong0PongReceiver(sid, role, s) => this.finishAndClose(s.sendStop())
            case PongPongReceiver(sid, role, s) => this.finishAndClose(s.sendStop())
        }

    def sendPingC[T: PongReceiver1or3](d: Data_Receiver, s: T): Done.type =
        s match {
            case Pong0PongReceiver(sid, role, s) => s.sendPingC().suspend(d, pongReceiver1or3)
            case PongPongReceiver(sid, role, s) => s.sendPingC().suspend(d, pongReceiver1or3)
        }*/

    /*def pongReceiver1(d: Data_Receiver, s: PongReceiver1): Done.type =
        s match {
            case Pong0PongReceiver(sid, role, s) =>
                println(s"${nameToString()} received Pong0")
                if (d.x <= 0) {
                    val end = s.sendStop()
                    this.finishAndClose(end)
                } else {
                    //println(s"${nameToString()} sending Ping #...")
                    d.x = d.x - 1
                    s.sendPingC().suspend(d, pongReceiver3)
                }
        }

    def pongReceiver3(d: Data_Receiver, s: PongReceiver3): Done.type =
        s match {
            case PongPongReceiver(sid, role, s) =>
                println(s"${nameToString()} received Pong")
                if (d.x <= 0) {
                    val f = s.sendStop()
                    finishAndClose(f)
                } else {
                    //println("\nPING\n")
                    d.x = d.x - 1
                    s.sendPingC().suspend(d, pongReceiver3)
                }
        }*/

    override def afterClosed(): Unit = TestPing.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestPing.handleException(cause, addr, sid)
}


/* Ponger */

case class Data_Ponger() extends Session.Data

object Ponger extends Actor("MyPonger") with ActorPonger {

    def spawn(): Unit = {
        this.spawn(TestPing.PORT_Ponger)
        registerPonger(TestPing.PORT_Ponger, "localhost", TestPing.PORT_Proto1, Data_Ponger(), pongerInit)
    }

    def pongerInit(d: Data_Ponger, s: Ponger1Suspend): Done.type = s.suspend(d, ponger1)

    def ponger1(d: Data_Ponger, s: Ponger1): Done.type =
        s match {
            case Ping0Ponger(sid, role, s) =>
                s.sendPong0().suspend(d, ponger3)
        }

    def ponger3(d: Data_Ponger, s: Ponger3): Done.type =
        s match {
            case PingPonger(sid, role, s) =>
                println(s"${nameToString()} received Ping")
                s.sendPong().suspend(d, ponger3)
            case StopPonger(sid, role, s) =>
                println(s"${nameToString()} received Stop")
                finishAndClose(s)
        }

    override def afterClosed(): Unit = TestPing.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestPing.handleException(cause, addr, sid)
}
