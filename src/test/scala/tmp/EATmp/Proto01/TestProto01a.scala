package tmp.EATmp.Proto01

import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestProto01a {

    val ca: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        println("Hello")

        val p1 = new Proto01
        p1.debug = true
        p1.spawn(8888)
        Thread.sleep(1000)

        AB.debug = true

        AB.spawn()

        println(ca.take())
        println(ca.take())

        AB.enqueueClose()
        p1.close()
        Thread.sleep(500)
    }
}


case class DataAa() extends Session.Data
case class DataBa() extends Session.Data


/* ... */

object AB extends Actor("MyAB") with ActorA with ActorB {

    def spawn(): Unit = {
        spawn(7777)
        registerA(7777, "localhost", 8888, DataAa(), a1)  // !!! mutable data
        registerB(7777, "localhost", 8888, DataBa(), b1Init)  // !!! mutable data
    }

    def a1(d: DataAa, s: A1): Done.type = {
        //Done  // testing linearity
        //s.sendL1(s"abc")  // testing linearity
        //val done = finishAndClose(s.sendL1(s"abc"))

        //* // !!! HERE FIXME `finish` breaking multi role in same session -- problem is PEER-socket-close in Actor.end
        val done = s.sendL1(s"abc").finish()
        TestProto01a.ca.add("A")
        done
        //*/

        /*
        s.sendL1(s"abc")
        s.isUsed = true
        TestProto01a.ca.add("A")
        Done
        */
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
    }

    def b1Init(d: DataBa, s: B1Suspend): Done.type = {
        //Done  // testing linearity
        s.suspend(d, b1)
        //s.suspend(b1)  // testing linearity
    }

    def b1(d: DataBa, s: B1): Done.type = {
        //Done  // testing linearity
        s match {
            case L1B(sid, role, x, s) =>
                //Done  // testing linearity
                //val done = finishAndClose(s)

                //*
                val done = s.finish()
                TestProto01a.ca.add("B")
                done
                //*/

                /*s.isUsed = true
                TestProto01a.ca.add("B")
                Done*/
        }
    }
}


/* ...

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
                val done = finishAndClose(s)
                TestProto01.c.add("B")
                done
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
    }
}
*/

