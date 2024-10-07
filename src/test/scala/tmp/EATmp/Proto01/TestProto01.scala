package tmp.EATmp.Proto01

import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress

object TestProto01 {

    def main(args: Array[String]): Unit = {
        println("Hello")

        /*val foo = new AP("Foo", Set("A", "B"))
        foo.spawn(8888)
        Thread.sleep(1000)
        FooA.spawn(); FooB.spawn()*/

        //val proto1 = new AP("Proto1", Set("A", "B"))
        val p1 = new Proto01
        p1.spawn(8888)
        Thread.sleep(1000)

        A.debug = true
        B.debug = true

        A.spawn(); B.spawn()
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

    def a1(d: DataA, s: A1): Done.type = {
        //Done  // testing linearity
        //s.sendL1(s"abc")  // testing linearity
        finishAndClose(s.sendL1(s"abc"))
    }

    override def handleException(addr: SocketAddress, sid: Option[Session.Sid]): Unit = {
        print(s"Channel exception from: ${addr}")
    }
}


/* ... */

object B extends Actor("MyB") with ActorB {

    def spawn(): Unit = {
        spawn(6666)
        registerB(6666, "localhost", 8888, DataB(), b1Init)  // !!! mutable data
    }

    def b1Init(d: DataB, s: B1Suspend): Done.type = {
        //Done  // testing linearity
        s.suspend(d, b1)
        //s.suspend(b1)  // testing linearity
    }

    def b1(d: DataB, s: B1): Done.type = {
        //Done  // testing linearity
        s match {
            case L1B(sid, role, x, s) =>
                //Done  // testing linearity
                finishAndClose(s)
        }
    }

    override def handleException(addr: SocketAddress, sid: Option[Session.Sid]): Unit = {
        print(s"Channel exception from: ${addr}")
    }
}

