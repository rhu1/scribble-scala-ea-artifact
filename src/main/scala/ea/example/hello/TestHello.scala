package ea.example.hello

import ea.example.hello.Hello.Proto1.*
import ea.runtime.Net.Port
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue


object TestHello {

    val PORT_Proto1: Port = A.PORT_Proto1

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val proto1 = new Proto1
        //proto1.debug = true
        proto1.spawn(PORT_Proto1)

        Thread.sleep(500)

        //A.debug = true
        //B.debug = true
        A.spawn()
        B.spawn()

        for i <- 1 to 2 do println(s"Closed ${shutdown.take()}.")
        println(s"Closing ${proto1.nameToString()}...")
        proto1.close()
    }

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}



/* A */

case class Data_A() extends Session.Data {}

object A extends Actor("MyA") with ActorA {

    val PORT_Proto1: Port = 8888
    private val PORT_A: Port = 7777

    def spawn(): Unit =
        this.spawn(PORT_A)
        this.registerA(PORT_A, "localhost", PORT_Proto1, Data_A(), a1)

    def a1(d: Data_A, s: A1): Done.type = s.sendL1().suspend(d, a2)
    
    def a2(d: Data_A, s: A2): Done.type = s match {
        case L2A(sid, role, x1, x2, x3, s) =>
            println(s"${nameToString()} received L2: $x1, $x2, $x3")
            this.finishAndClose(s)
        }

    /* Close */

    override def afterClosed(): Unit = TestHello.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestHello.handleException(cause, addr, sid)
}


/* B */

case class Data_B() extends Session.Data {}

object B extends Actor("MyB") with ActorB {

    private val PORT_B: Port = 6666

    def spawn(): Unit =
        this.spawn(PORT_B)
        this.registerB(PORT_B, "localhost", TestHello.PORT_Proto1, Data_B(), b1Init)

    def b1Init(d: Data_B, s: B1Suspend): Done.type = s.suspend(d, b1)

    def b1(d: Data_B, s: B1): Done.type = s match {
        case L1B(sid, role, s) =>
            println(s"${nameToString()} received L1")
            this.finishAndClose(s.sendL2(42, "hello", true))
        }

    /* Close */

    override def afterClosed(): Unit = TestHello.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        TestHello.handleException(cause, addr, sid)
}
