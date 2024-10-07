package tmp.EATmp.Proto09

import ea.runtime.{Actor, Done, Session}
import tmp.EATmp.Proto09.{A1, ActorA}
import tmp.EATmp.*

import java.net.SocketAddress

object TestProto09 {

    def main(args: Array[String]): Unit = {
        println("Hello")

        val p09 = new Proto09.Proto09
        p09.spawn(8888)
        Thread.sleep(1000)

        A.debug = true
        B.debug = true

        A.spawn();
        B.spawn()
    }
}


case class DataA() extends Session.Data
case class DataB() extends Session.Data


/* ... */

object A extends Actor("MyA") with ActorA {

    def spawn(): Unit = {
        spawn(7777)
        registerA(7777, "localhost", 8888, DataA(), a1)  // !!! mutable data
    }

    def a1(d: DataA, s: Proto09.A1): Done.type = {
        s.sendL1().suspend(d, a2)
    }
    
    def a2(d: DataA, s: Proto09.A2): Done.type = {
        s match {
            case Proto09.L2A(sid, x1, x2, s) =>
                finishAndClose(s)
        }
    }

    override def handleException(addr: SocketAddress): Unit = {
       print(s"Channel exception from: ${addr}")
    }
}


/* ... */

object B extends Actor("MyB") with Proto09.ActorB {

    def spawn(): Unit = {
        spawn(6666)
        registerB(6666, "localhost", 8888, DataB(), b1Init)  // !!! mutable data
    }

    def b1Init(d: DataB, s: Proto09.B1Suspend): Done.type = {
        s.suspend(d, b1)
    }

    def b1(d: DataB, s: Proto09.B1): Done.type = {
        s match {
            case Proto09.L1B(sid, s) =>
                finishAndClose(s.sendL2(42, true))
        }
    }

    override def handleException(addr: SocketAddress): Unit = {
        print(s"Channel exception from: ${addr}")
    }
}

