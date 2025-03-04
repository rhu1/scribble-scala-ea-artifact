package ea.example.savina.pingself

import ea.example.savina.pingself.PingSelf.Proto1.*
import ea.example.savina.pingself.PingSelf.ProtoC.*
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestPingSelf {

    val PORT_Proto1 = 8888
    val PORT_ProtoC = 8889
    val PORT_C = 5555
    val PORT_Pinger = 6666
    val PORT_Ponger = 9999

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val ap_Proto1 = new Proto1
        //ap_Proto1.debug = true
        ap_Proto1.spawn(PORT_Proto1)

        val ap_ProtoC = new ProtoC
        //ap_ProtoC.debug = true
        ap_ProtoC.spawn(PORT_ProtoC)

        Thread.sleep(500)

        //C.debug = true
        //Pinger.debug = true
        //Ponger.debug = true
        Ponger.spawn()
        Pinger.spawn()
        C.spawn()

        for i <- 1 to 3 do println(s"Closed ${shutdown.take()}.")  // C, Pinger, Ponger
        println(s"Closing ${ap_Proto1.nameToString()}...")
        ap_Proto1.close()
        println(s"Closing ${ap_ProtoC.nameToString()}...")
        ap_ProtoC.close()
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* C */

case class Data_C() extends Session.Data

object C extends Actor("MyC") with ActorC {

    def spawn(): Unit = {
        this.spawn(TestPingSelf.PORT_C)
        this.registerC(TestPingSelf.PORT_C, "localhost", TestPingSelf.PORT_ProtoC, Data_C(), c1)
    }

    def c1(d: Data_C, s: C1): Done.type = s.sendStart().suspend(d, c2)

    def c2(d: Data_C, s: C2): Done.type =
        s match {
            case StopCC(sid, role, s) => finishAndClose(s)
        }

    override def afterClosed(): Unit = TestPingSelf.shutdown.add(this.pid);

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestPingSelf.handleException(cause, addr, sid)
}


/* Pinger */

case class Data_Pinger(var rem: Int) extends Session.Data {
    var pingDecision: Boolean = true  // Decided+sent by PingDecisionMaker, stored by PingDecisionReceiver
    var pinger3: Session.LinOption[Pinger3] = Session.LinNone()
    var maker1: Session.LinOption[PingDecisionMaker1] = Session.LinNone()
}

object Pinger extends Actor("MyPinger") with ActorPinger with ActorPingDecisionMaker with ActorPingDecisionReceiver {
    val REPEATS = 2

    def spawn(): Unit = {
        this.spawn(TestPingSelf.PORT_Pinger)
        val d = Data_Pinger(REPEATS)
        this.registerPingDecisionMaker(TestPingSelf.PORT_Pinger, "localhost", TestPingSelf.PORT_ProtoC, d, maker1Init)
        this.registerPingDecisionReceiver(TestPingSelf.PORT_Pinger, "localhost", TestPingSelf.PORT_ProtoC, d, pingDecisionReceiverInit)
    }

    def pinger1(d: Data_Pinger, s: Pinger1): Done.type = s.sendPing0().suspend(d, pinger2)

    def pinger2(d: Data_Pinger, s: Pinger2): Done.type =
        s match {
            case Pong0Pinger(sid, role, s) =>
                println(s"${nameToString()} received Pong0")
                // freeze Pinger3 and become PingDecisionMaker
                val (a, done) = Session.freeze(s,
                    (sid: Session.Sid, role: Session.Role, a: Actor) => Pinger3(sid, role, a))
                d.pinger3 = a
                d.maker1 match {
                    case _: Session.LinNone =>
                    case y: Session.LinSome[_] => Session.become(d, y, maker1)
                }
                done
        }

    def pinger3(d: Data_Pinger, s: Pinger3): Done.type =
        if (d.pingDecision) {
            s.sendPing().suspend(d, pinger4)
        } else {
            val end = s.sendStop()
            pingerClose()
            end.finish()
        }

    def pinger4(d: Data_Pinger, s: Pinger4): Done.type =
        s match {
            case PongPinger(sid, role, s) =>
                println(s"${nameToString()} received Pong")
                val (a, done) = Session.freeze(s,
                    (sid: Session.Sid, role: Session.Role, a: Actor) => Pinger3(sid, role, a))
                d.pinger3 = a
                d.maker1 match {
                    case _: Session.LinNone =>
                    case y: Session.LinSome[_] => Session.become(d, y, maker1)
                }
                done
        }

    def maker1Init(d: Data_Pinger, s: PingDecisionMaker1): Done.type = {
        val (a, done) = Session.freeze(s,
            (sid: Session.Sid, role: Session.Role, a: Actor) => PingDecisionMaker1(sid, role, a))
        d.maker1 = a
        done
    }

    def maker1(d: Data_Pinger, s: PingDecisionMaker1): Done.type =
        if (d.rem <= 0) {
            val done = s.sendStopC().sendStopC().finish()
            pingerClose()
            done
        } else {
            d.rem = d.rem - 1
            println(s"${nameToString()}(PingDecisionMaker) sending Ping, remaining ${d.rem}...")
            val s1 = s.sendPing()
            val (a, done) = Session.freeze(s1,
                (sid: Session.Sid, role: Session.Role, a: Actor) => PingDecisionMaker1(sid, role, a))
            d.maker1 = a
            done
        }

    def pingDecisionReceiverInit(d: Data_Pinger, s: PingDecisionReceiver1Suspend): Done.type =
        s.suspend(d, receiver1)

    def receiver1(d: Data_Pinger, s: PingDecisionReceiver1): Done.type =
        s match {
            case StartPingDecisionReceiver(sid, role, s) =>
                registerPinger(TestPingSelf.PORT_Pinger, "localhost", TestPingSelf.PORT_Proto1, d, pinger1)
                s.suspend(d, receiver2)
        }

    def receiver2(d: Data_Pinger, s: PingDecisionReceiver2): Done.type = {
        val done = s match {
            case PingPingDecisionReceiver(sid, role, s) =>
                println(s"${nameToString()}(PingDecisionReceiver) received Ping")
                d.pingDecision = true
                s.suspend(d, receiver2)
            case StopCPingDecisionReceiver(sid, role, s) =>
                d.pingDecision = false
                pingerClose()
                s.finish()
        }
        // become Pinger
        d.pinger3 match {
            case _: Session.LinNone =>
            case y: Session.LinSome[_] => Session.become(d, y, pinger3)
        }
        done
    }

    private var pingerShutdown = 3;  // Pinger, PingDecisionReceiver, PingDecisionMaker

    def pingerClose(): Unit = {
        this.pingerShutdown -= 1
        if (this.pingerShutdown == 0) {
            this.enqueueClose()
        }
    }

    override def afterClosed(): Unit = TestPingSelf.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestPingSelf.handleException(cause, addr, sid)
}


/* Ponger */

case class Data_Ponger() extends Session.Data

object Ponger extends Actor("MyPonger") with ActorPonger {

    def spawn(): Unit = {
        this.spawn(TestPingSelf.PORT_Ponger)
        this.registerPonger(TestPingSelf.PORT_Ponger, "localhost", TestPingSelf.PORT_Proto1, Data_Ponger(), pongerInit)
    }

    def pongerInit(d: Data_Ponger, s: Ponger1Suspend): Done.type = s.suspend(d, ponger1)

    def ponger1(d: Data_Ponger, s: Ponger1): Done.type =
        s match {
            case Ping0Ponger(sid, role, s) =>
                println(s"${nameToString()} received Ping0")
                s.sendPong0().suspend(d, ponger3)
        }

    def ponger3(d: Data_Ponger, s: Ponger3): Done.type =
        s match {
            case PingPonger(sid, role, s) =>
                println(s"${nameToString()} received Ping")
                s.sendPong().suspend(d, ponger3)
            case StopPonger(sid, role, s) => finishAndClose(s)
        }

    override def afterClosed(): Unit = TestPingSelf.shutdown.add(this.pid);

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestPingSelf.handleException(cause, addr, sid)
}
