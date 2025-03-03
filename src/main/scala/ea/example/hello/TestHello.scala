package ea.example.hello

import ea.runtime.{Actor, Done, Session}

import java.util.concurrent.LinkedTransferQueue

import java.net.SocketAddress

object TestHello {

    val PORT_hello = 8888
    val PORT_A = 7777
    val PORT_B = 6666

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val ap_hello = new Hello
        //ap_hello.debug = true
        ap_hello.spawn(PORT_hello)

        Thread.sleep(500)

        //A.debug = true
        //B.debug = true
        A.spawn();
        B.spawn()

        println(s"Closed: ${shutdown.take()}")
        println(s"Closed: ${shutdown.take()}")

        ap_hello.close()
    }
}



/* ... */

case class DataA() extends Session.Data

object A extends Actor("MyA") with ActorA {

    def spawn(): Unit = {
        this.spawn(TestHello.PORT_A)
        this.registerA(TestHello.PORT_A, "localhost", TestHello.PORT_hello, DataA(), a1)
    }

    def a1(d: DataA, s: A1): Done.type = {
        s.sendL1().suspend(d, a2)
    }
    
    def a2(d: DataA, s: A2): Done.type = {
        s match {
            case L2A(sid, role, x1, x2, x3, s) =>
                println(s"${nameToString()} received L2: $x1, $x2, $x3")
                this.finishAndClose(s)
        }
    }

    override def afterClosed(): Unit = {
        TestHello.shutdown.add(this.pid);
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* ... */

case class DataB() extends Session.Data

object B extends Actor("MyB") with ActorB {

    def spawn(): Unit = {
        this.spawn(TestHello.PORT_B)
        this.registerB(TestHello.PORT_B, "localhost", TestHello.PORT_hello, DataB(), b1Init)
    }

    def b1Init(d: DataB, s: B1Suspend): Done.type = {
        s.suspend(d, b1)
    }

    def b1(d: DataB, s: B1): Done.type = {
        s match {
            case L1B(sid, role, s) =>
                println(s"${nameToString()} received L1")
                this.finishAndClose(s.sendL2(42, "hello", true))
        }
    }

    override def afterClosed(): Unit = {
        TestHello.shutdown.add(this.pid);
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}
