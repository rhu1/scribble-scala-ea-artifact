package tmp.EATmp.Proto10

import ea.runtime.{Actor, Done, Session}
import tmp.EATmp.Proto10

import java.net.SocketAddress

object TestProto10 {

    def main(args: Array[String]): Unit = {
        println("Hello")

        val p10 = new Proto10.Proto10
        p10.spawn(8888)
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

    def main(args: Array[String]): Unit = {
        val p10 = new Proto10.Proto10
        p10.spawn(8888)
        Thread.sleep(1000)

        //this.debug = true
        spawn();
    }

    def spawn(): Unit = {
        spawn(7777)
        registerA(7777, "localhost", 8888, DataA(), a1)  // !!! mutable data
    }

    def a1(d: DataA, s: Proto10.A1): Done.type = {
        registerA(7777, "localhost", 8888, DataA(), a1)  // XXX what if init failure before here? maybe register in failure handler (but maybe already registered?)
        println(s"(${s.sid}) A sending L1...")
        s.sendL1().suspend(d, a2)
    }
    
    def a2(d: DataA, s: Proto10.A2): Done.type = {
        s match {
            case Proto10.L2A(sid, s) =>
                println(s"(${sid}) A received L2.")
                Thread.sleep(1000)
                println(s"(${sid}) A sending L1...")
                s.sendL1().suspend(d, a2)
        }
    }

    override def handleException(addr: SocketAddress): Unit = {
        println(s"Channel exception from: ${addr}")
    }
}


/* ... */

object B extends Actor("MyB") with Proto10.ActorB {

    def main(args: Array[String]): Unit = {
        //this.debug = true
        spawn();
    }

    def spawn(): Unit = {
        spawn(6666)
        registerB(6666, "localhost", 8888, DataB(), b1Init)  // !!! mutable data
    }

    def b1Init(d: DataB, s: Proto10.B1Suspend): Done.type = {
        s.suspend(d, b1)
    }

    def b1(d: DataB, s: Proto10.B1): Done.type = {
        s match {
            case Proto10.L1B(sid, s) =>
                println(s"(${sid}) B received L1.")
                println(s"(${sid}) B sending L2...")
                s.sendL2().suspend(d, b1)
        }
    }

    override def handleException(addr: SocketAddress): Unit = {
        println(s"Channel exception from: ${addr}")
    }
}

