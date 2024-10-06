package ea.test.basic.proto01

import ea.runtime.{Actor, Done, Session}
import ea.test.basic.proto01.proto1.{A1, ActorA}

object TestGen01 {

    def main(args: Array[String]): Unit = {
        println("Hello")

        /*val foo = new AP("Foo", Set("A", "B"))
        foo.spawn(8888)
        Thread.sleep(1000)
        FooA.spawn(); FooB.spawn()*/

        //val proto1 = new AP("Proto1", Set("A", "B"))
        val p1 = new proto1.Proto1
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

object A extends proto1.ActorA("MyA") {

    def spawn(): Unit = {
        spawn(7777)
        register(7777, "localhost", 8888, DataA(), a1)  // !!! mutable data
    }

    def a1(d: DataA, s: proto1.A1): Done.type = {
        //Done  // testing linearity
        //s.sendL1(s"abc")  // testing linearity
        finishAndClose(s.sendL1(s"abc"))
    }
}


/* ... */

object B extends proto1.ActorB("MyB") {

    def spawn(): Unit = {
        spawn(6666)
        register(6666, "localhost", 8888, DataB(), b1Init)  // !!! mutable data
    }

    def b1Init(d: DataB, s: proto1.B1Suspend): Done.type = {
        //Done  // testing linearity
        s.suspend(d, b1)
        //s.suspend(b1)  // testing linearity
    }

    def b1(d: DataB, s: proto1.B1): Done.type = {
        //Done  // testing linearity
        s match {
            case proto1.L1B(sid, x, s) =>
                //Done  // testing linearity
                finishAndClose(s)
        }
    }
}

