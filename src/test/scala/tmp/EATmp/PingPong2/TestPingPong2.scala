package tmp.EATmp.PingPong2

import ea.runtime.{Actor, Done, Session}
import tmp.EATmp.PingPong2C.{ActorC, ActorPingDecisionMaker, ActorPingDecisionReceiver, C1, C2, PingDecisionMaker1, PingDecisionReceiver1, PingDecisionReceiver1Suspend, PingDecisionReceiver2, PingDecisionReceiver2Suspend, PingPingDecisionReceiver, PingPong2C, StartPingDecisionReceiver, StopCC, StopCPingDecisionReceiver}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestPingPong2 {

    val c: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        println("Hello")

        val p2 = new PingPong2
        //p2.debug = true
        p2.spawn(8888)

        val p2c = new PingPong2C
        //p2c.debug = true
        p2c.spawn(8889)
        Thread.sleep(1000)

        //C.debug = true
        //Ponger.debug = true
        Pinger.debug = true

        Ponger.spawn()
        Pinger.spawn()
        C.spawn()

        println(s"1: ${c.take()}")
        println(s"2: ${c.take()}")
        println(s"3: ${c.take()}")
        println(s"4: ${c.take()}")
        println(s"5: ${c.take()}")
        p2.close()
        p2c.close()
        Thread.sleep(500)
        Pinger.enqueueClose()
    }
}


case class DataA() extends Session.Data
case class DataB(var x: Int) extends Session.Data {
    var pingDecision: Boolean = true
    var p3: Session.LinOption[Pinger3] = Session.LinNone()
    var m1: Session.LinOption[PingDecisionMaker1] = Session.LinNone()
}
case class DataD() extends Session.Data


/* ... */

object C extends Actor("MyC") with ActorC {

    def spawn(): Unit = {
        spawn(5555)
        registerC(5555, "localhost", 8889, DataA(), c1)
    }

    def c1(d: DataA, s: C1): Done.type = {
        s.sendStart().suspend(d, c2)
    }

    def c2(d: DataA, s: C2): Done.type = {
        s match {
            case StopCC(sid, role, s) =>
                TestPingPong2.c.add("C")  // finishAndClose can throw IOException
                Thread.sleep(500)
                val done = finishAndClose(s)
                done
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        //TestPingPong2.c.add("C")

        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
    }
}


/* ... */

object Pinger extends Actor("MyPinger") with ActorPinger with ActorPingDecisionMaker with ActorPingDecisionReceiver {

    def spawn(): Unit = {
        spawn(6666)
        val d = DataB(2)
        registerPingDecisionMaker(6666, "localhost", 8889, d, maker1Init)
        registerPingDecisionReceiver(6666, "localhost", 8889, d, pingDecisionReceiverInit)
    }

    def pinger1(d: DataB, s: Pinger1): Done.type = {
        s.sendPing0().suspend(d, pinger2)
    }

    def pinger2(d: DataB, s: Pinger2): Done.type = {
        s match {
            case Pong0Pinger(sid, role, s) =>
                //s.sendPing().suspend(d, pinger3)

                // !!! freeze Pinger and become PingDecisionMaker
                val (a, done) = Session.freeze(s,
                    (sid: Session.Sid, role: Session.Role, a: Actor) => Pinger3(sid, role, a))
                d.p3 = a

                d.m1 match {
                    case _: Session.LinNone => // !!! type case
                    case y: Session.LinSome[PingDecisionMaker1] =>
                        println("\nfoo1\n")
                        Session.become(d, y, maker1)
                }

                done
        }
    }

    def pinger3(d: DataB, s: Pinger3): Done.type = {
        if (d.pingDecision) {
            s.sendPing().suspend(d, pinger4)
        } else {
            val f = s.sendStop()

            println("\nSTOP3\n")

            TestPingPong2.c.add("Pinger")
            f.finish()
        }
    }

    def pinger4(d: DataB, s: Pinger4): Done.type = {
        s match {
            case PongPinger(sid, role, s) =>
                val (a, done) = Session.freeze(s,
                    (sid: Session.Sid, role: Session.Role, a: Actor) => Pinger3(sid, role, a))
                d.p3 = a

                d.m1 match {
                    case _: Session.LinNone => // !!! type case
                    case y: Session.LinSome[PingDecisionMaker1] =>
                        println("\nfoo1\n")
                        Session.become(d, y, maker1)
                }

                done
        }
    }

    def maker1Init(d: DataB, s: PingDecisionMaker1): Done.type = {
        val (a, done) = Session.freeze(s,
            (sid: Session.Sid, role: Session.Role, a: Actor) => PingDecisionMaker1(sid, role, a))
        d.m1 = a
        done
    }

    def maker1(d: DataB, s: PingDecisionMaker1): Done.type = {
        println(s"\nfoo2 ${d.x}\n")
        if (d.x <= 0) {
            val f = s.sendStopC().sendStopC()
            TestPingPong2.c.add("PingDecisionMaker")
            f.finish()
        } else {
            d.x = d.x - 1
            // freeze
            val s1 = s.sendPing()
            val (a, done) = Session.freeze(s1,
                (sid: Session.Sid, role: Session.Role, a: Actor) => PingDecisionMaker1(sid, role, a))
            d.m1 = a
            done
        }
    }

    def pingDecisionReceiverInit(d: DataB, s: PingDecisionReceiver1Suspend): Done.type = {
        val done = s.suspend(d, receiver1)
        registerPinger(6666, "localhost", 8888, d, pinger1)
        done
    }

    def receiver1(d: DataB, s: PingDecisionReceiver1): Done.type = {
        s match {
            case StartPingDecisionReceiver(sid, role, s) =>
                s.suspend(d, receiver2)
        }
    }

    def receiver2(d: DataB, s: PingDecisionReceiver2): Done.type = {
        var done: Option[Done.type] = None
        s match {
            case PingPingDecisionReceiver(sid, role, s) =>
                d.pingDecision = true
                done = Some(s.suspend(d, receiver2))
            case StopCPingDecisionReceiver(sid, role, s) =>
                d.pingDecision = false

                println("\nSTOP\n")

                TestPingPong2.c.add("PingDecisionReceiver")
                done = Some(s.finish())
        }

        // become Pinger
        d.p3 match {
            case _: Session.LinNone => // !!! type case
            case y: Session.LinSome[Pinger3] =>
                Session.become(d, y, pinger3)
        }

        done.get
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        //TestPingPong1.c.add("Pinger")

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
        s match {
            /*case PingPonger(sid, role, s) =>  // FIXME
                s.sendPong().suspend(d, ponger3) */
            case Ping0Ponger(sid, role, s) =>
                println("\nPong0\n")
                s.sendPong0().suspend(d, ponger3)
        }
    }

    def ponger3(d: DataD, s: Ponger3): Done.type = {
        s match {
            case PingPonger(sid, role, s) =>  // !!! PingPonger3 -- not PingPonger

                println("\nPong\n")

                s.sendPong().suspend(d, ponger3)
            case StopPonger(sid, role, s) =>
                TestPingPong2.c.add("Ponger")
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
