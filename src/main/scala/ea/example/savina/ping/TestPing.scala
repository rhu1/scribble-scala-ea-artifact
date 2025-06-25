package ea.example.savina.ping

import ea.example.savina.ping.Ping.Proto1.*
import ea.runtime.Net.Port
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestPing {

    val PORT_Proto1 = Ponger.PORT_Proto1

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val proto1 = new Proto1
        //proto1.debug = true
        proto1.spawn(PORT_Proto1)

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
        println(s"Closing ${proto1.nameToString()}...")
        proto1.close()
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}


/* C */

case class Data_C() extends Session.Data

object C extends Actor("MyC") with ActorC {

    private val PORT_C: Port = 5555

    def spawn(): Unit =
        this.spawn(PORT_C)
        this.registerC(PORT_C, "localhost", TestPing.PORT_Proto1, Data_C(), c1)

    def c1(d: Data_C, s: C1): Done.type = s.sendStart().suspend(d, c2)

    def c2(d: Data_C, s: C2): Done.type = s match {
        case StopC(sid, role, s) => this.finishAndClose(s)
    }

    /* Close */

    override def afterClosed(): Unit = TestPing.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestPing.handleException(cause, addr, sid)
}


/* Pinger */

case class Data_Pinger() extends Session.Data

object Pinger extends Actor("MyPinger") with ActorPinger {

    private val PORT_Pinger: Port = 6666
    
    def spawn(): Unit =
        this.spawn(PORT_Pinger)
        this.registerPinger(PORT_Pinger, "localhost",
            TestPing.PORT_Proto1, Data_Pinger(), pingerInit)

    def pingerInit(d: Data_Pinger, s: Pinger1Suspend): Done.type = s.suspend(d, pinger1)

    def pinger1(d: Data_Pinger, s: Pinger1): Done.type = s match {
        case StartPinger(sid, role, s) =>
            println(s"${nameToString()} received Start")
            s.sendPing0().suspend(d, pinger3)
    }

    def pinger3(d: Data_Pinger, s: Pinger3): Done.type = s match {
        case PingCPinger(sid, role, s) =>
            println(s"${nameToString()} received PingC")
            s.sendPing().suspend(d, pinger3)
        case StopPinger(sid, role, s) =>
            val end = s.sendStop().sendStop()
            //println("\nSTOP\n")
            this.finishAndClose(end)
    }

    /* Close */

    override def afterClosed(): Unit = TestPing.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestPing.handleException(cause, addr, sid)
}


/* PongReceiver */

case class Data_Receiver(var rem: Int) extends Session.Data

object PongReceiver extends Actor("MyPongReceiver") with ActorPongReceiver {

    private val PORT_PongReceiver: Port = 7777
    
    val REPEATS = 2

    def spawn(): Unit = {
        this.spawn(PORT_PongReceiver)
        this.registerPongReceiver(PORT_PongReceiver, "localhost",
            TestPing.PORT_Proto1, Data_Receiver(REPEATS), pongReceiverInit)
    }

    def pongReceiverInit(d: Data_Receiver, s: PongReceiver1Suspend): Done.type =
        s.suspend(d, pongReceiver1or3)

    def pongReceiver1or3[T](d: Data_Receiver, s: T)(implicit ev: PongReceiver1or3[T]): Done.type = {
        ev.print(s)
        if (d.rem <= 0) {
            //stop(d, s)
            ev.stop(this, d, s)
        } else {
            d.rem = d.rem - 1
            println(s"${nameToString()} sending PingC, remaining ${d.rem}...")
            ev.sendPingC(this, d, s)
        }
    }

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

    /* Close */

    override def afterClosed(): Unit = TestPing.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestPing.handleException(cause, addr, sid)
}


/* Ponger */

case class Data_Ponger() extends Session.Data

object Ponger extends Actor("MyPonger") with ActorPonger {

    val PORT_Proto1: Port = 8888
    private val PORT_Ponger: Port = 9999
    
    def spawn(): Unit =
        this.spawn(PORT_Ponger)
        registerPonger(PORT_Ponger, "localhost",
            TestPing.PORT_Proto1, Data_Ponger(), pongerInit)

    def pongerInit(d: Data_Ponger, s: Ponger1Suspend): Done.type = s.suspend(d, ponger1)

    def ponger1(d: Data_Ponger, s: Ponger1): Done.type = s match {
        case Ping0Ponger(sid, role, s) =>
            s.sendPong0().suspend(d, ponger3)
    }

    def ponger3(d: Data_Ponger, s: Ponger3): Done.type = s match {
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
