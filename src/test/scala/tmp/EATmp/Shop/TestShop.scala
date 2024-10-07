package tmp.EATmp.Shop

import ea.runtime.{Actor, Done, Session}
import tmp.EATmp.ShopProto1.*
import tmp.EATmp.ShopProto2

import java.net.SocketAddress

object TestShop {

    def main(args: Array[String]): Unit = {
        println("Hello")

        val proto1 = new ShopProto1
        proto1.spawn(8888)
        val proto2 = new ShopProto2.ShopProto2
        proto2.spawn(9999)
        Thread.sleep(1000)

        //C.debug = true
        //SF.debug = true

        SF.spawn()
        S.spawn()
        //Thread.sleep(1000)  // !!! make sure Staff established

        P.spawn()
        C.spawn()
    }
}


/* ... */

class DataC(var ids: collection.mutable.Seq[String]) extends Session.Data {
    var tmp = collection.mutable.Seq[String]()
}

object C extends Actor("Customer") with ActorC {

    val port = 7777

    def spawn(): Unit = {
        val d = new DataC(collection.mutable.Seq())
        spawn(this.port)
        registerC(this.port, "localhost", 8888, d, cInit)
    }

    def cInit(d: DataC, s: C1): Done.type = {
        s.sendReqItems("()").suspend(d, c2)
    }

    def c2(d: DataC, s: C2): Done.type = {
        s match {
            case ItemsC(sid, role, x, s) =>
                d.ids ++= x.split("::")
                d.tmp ++= x.split("::")
                c3(d, s)
        }
    }

    def c3(d: DataC, s: C3): Done.type = {
        if (d.tmp.nonEmpty) {
            val h = d.tmp.head
            d.tmp = d.tmp.tail
            s.sendGetItemInfo(h).suspend(d, c4)
        } else  {
            Thread.sleep(500)
            s.sendCheckout(d.ids(1)).suspend(d, c5)
        }
    }

    def c4(d: DataC, s: C4): Done.type = {
        s match {
            case ItemInfoC(sid, role, x, s) => c3(d, s)
        }
    }

    def c5(d: DataC, s: C5): Done.type = {
        s match {
            case ProcessingC(sid, role, x, s) => s.suspend(d, c6)
            case OutOfStockC(sid, role, x, s) => c3(d, s)
        }
    }

    def c6(d: DataC, s: C6): Done.type = {
        s match {
            case OKcC(sid, role, x, s) => c3(d, s)
            case DeclinedcC(sid, role, x, s) => c3(d, s)
        }
    }

    override def handleException(addr: SocketAddress, sid: Option[Session.Sid]): Unit = {
        print(s"Channel exception from: ${addr}")
    }
}


/* ... */

// id -> (price, num)
class DataS(val stock: collection.mutable.Map[String, (Int, Int)]) extends Session.Data {

    var ss1: Session.LinOption[ShopProto2.SS1] = Session.LinNone()

    def inStock(k: String): Boolean = this.stock.contains(k) && this.stock(k)._2 > 0
    def summary(): String = this.stock.keySet.mkString("::")
    def cost(k: String, n: Int): Int = this.stock(k)._1 * n

    var oos: String = ""
}

trait SS extends ActorS with ShopProto2.ActorSS

object S extends Actor("Shop") with SS {

    val port = 6666

    def spawn(): Unit = {
        val stock = collection.mutable.Map[String, (Int, Int)](
            "item1" -> (10, 0),
            "item2" -> (20, 0))
        val d = new DataS(stock)
        spawn(this.port)
        registerSS(this.port, "localhost", 9999, d, ss1Cache)
        //register(this.port, "localhost", 8888, d, s1suspend)  // !!! Move to ss1 ?
    }

    def ss1Cache(d: DataS, s: ShopProto2.SS1): Done.type = {
        //val done = Session.cache(s, (sid: Session.Sid, a: Actor) => SS1(sid, a), (a: Some[SS1]) => d.ss1 = a)  // s.cache((a: Some[SS1]) => d.ss1 = a)
        val (a, done) = Session.freeze(s, (sid: Session.Sid, role: Session.Role, a: Actor) => ShopProto2.SS1(sid, role, a))
        d.ss1 = a
        registerS(this.port, "localhost", 8888, d, s1suspend)
        done
    }

    def s1suspend(d: DataS, s: S1Suspend): Done.type = {
        //s.suspend(d, s1)
        s.suspend(d, custReqHandler[S1])
    }

    def s1(d: DataS, s: S1): Done.type = {
        custReqHandler(d, s)
    }

    sealed trait S1orS3[T]
    object S1orS3 {
        implicit val T_S1: S1orS3[S1] = new S1orS3[S1] {}
        implicit val T_S3: S1orS3[S3] = new S1orS3[S3] {}
    }
    /*def isS1orS3[T: S1orS3](t: T): String = t match {
        case i: S1 => "%d is an Integer".format(i)
        case s: S3 => "%s is a String".format(s)
    }*/

    // !!! S1orS3
    def custReqHandler[T: S1orS3](d: DataS, s: T): Done.type = {
        s match {
            case s: S1 =>
                println("S1")
                s match {
                    case ReqItemsS(sid, role, x, s) =>
                        s.sendItems(d.summary())
                         //.suspend(d, s3)
                         .suspend(d, custReqHandler[S3])
                }
            case s: S3 =>
                println("S3")
                s match {
                    case GetItemInfoS(sid, role, x, s) =>
                        val info = d.stock(x)
                        val pay = s"${info._1}@${info._2}"
                        //s.sendItemInfo(pay).suspend(d, s3)
                        s.sendItemInfo(pay).suspend(d, custReqHandler[S3])
                    case CheckoutS(sid, role, x, s) =>
                        if (d.inStock(x)) {
                            val pay = s"${d.cost(x, 1)}"
                            s.sendProcessing(s"Processing${x}")
                             .sendBuy(pay)
                             .suspend(d, paymentResponseHandler)
                        } else {
                            val sus = s.sendOutOfStock(s"OutOfStock${x}")
                            d.oos = x
                            // !!! assumes staff established -- Option vs. affine
                            d.ss1 match {
                                case _: Session.LinNone => // !!! type case
                                case y: Session.LinSome[ShopProto2.SS1] =>
                                    Session.become(d, y, restockHandler)  // [[d.ss1]].become(d, ss1)
                            }
                            sus.suspend(d, custReqHandler[S3])
                        }
                }
        }
    }

    def restockHandler(d: DataS, s: ShopProto2.SS1): Done.type = {
        ss1(d, s)
    }

    def ss1(d: DataS, s: ShopProto2.SS1): Done.type = {
        println(s"become: add${d.oos}")
        ss1Cache(d, s.sendAddItem(s"add${d.oos}"))
    }

    def s3(d: DataS, s: S3): Done.type = {
        custReqHandler(d, s)
    }

    def paymentResponseHandler(d: DataS, s: S7): Done.type = {
        s match {
            case OKS(sid, role, x, s) => s.sendOKc("delivery date").suspend(d, s3)
            case DeclinedS(sid, role, x, s) => s.sendDeclinedc("insufficient funds").suspend(d, s3)
        }
    }

    override def handleException(addr: SocketAddress, sid: Option[Session.Sid]): Unit = {
        print(s"Channel exception from: ${addr}")
    }
}


/* ... */

class DataSF extends Session.Data

object SF extends Actor("Staff") with ShopProto2.ActorSF {

    val port = 4444

    def spawn(): Unit = {
        spawn(this.port)
        registerSF(this.port, "localhost", 9999, new DataSF, sf1suspend)
    }

    def sf1suspend(d: DataSF, s: ShopProto2.SF1Suspend): Done.type = {
        s.suspend(d, sf1)
    }

    def sf1(d: DataSF, s: ShopProto2.SF1): Done.type = {
        s match {
            case ShopProto2.AddItemSF(sid, role, x, s) => s.suspend(d, sf1)
            case ShopProto2.RemoveItemSF(sid, role, x, s) => s.suspend(d, sf1)
        }
    }

    override def handleException(addr: SocketAddress, sid: Option[Session.Sid]): Unit = {
        print(s"Channel exception from: ${addr}")
    }
}


/* ... */

class DataP extends Session.Data

object P extends Actor("PaymentProcessor") with ActorP {

    val port = 5555

    def spawn(): Unit = {
        spawn(this.port)
        registerP(this.port, "localhost", 8888, new DataP, p1suspend)
    }

    def p1suspend(d: DataP, s: P1Suspend): Done.type = {
        s.suspend(d, p1)
    }

    def p1(d: DataP, s: P1): Done.type = {
        s match {
            case BuyP(sid, role, x, s) =>
                if (true) {
                    s.sendOK("()").suspend(d, p1)
                } else {
                    s.sendDeclined("()").suspend(d, p1)
                }
        }
    }

    override def handleException(addr: SocketAddress, sid: Option[Session.Sid]): Unit = {
        print(s"Channel exception from: ${addr}")
    }
}














































/*
/* ... */

object oldS extends Actor("Shop") {

    def spawn(): Unit = {
        spawnAndRegister("localhost", 8888, "Proto1",
            "S", 7777, s1suspend)
    }

    def spawnAndRegister(apHost: String, apPort: Int, proto: Session.Global, r: Session.Role,
                         port: Int, f: S1Suspend => Done.type): Unit = {
        spawn(port)
        val g = (sid: Session.Sid) => f.apply(S1Suspend(sid, this))
        enqueueForSelectLoop(() => registerForPeers2(apHost, apPort, proto, r, port, g, Set("P", "C")))
    }

    def s1suspend(s: S1Suspend): Done.type = {
        s.suspend(s1)
    }

    def s1(s: S1): Done.type = {
        s match {
            case ReqItemsS(sid, x, s) => s.sendItems("").suspend(s3)
        }
    }

    var i = 0

    def s3(s: S3): Done.type = {
        s match {
            case ItemInfoS(sid, x, s) => s.suspend(s3)
            case CheckoutS(sid, x, s) => {
                i += 1
                if (i % 2 == 0) {
                    s.sendProcessing(s"Processing${i}").sendBuy("").suspend(s6)
                } else {
                    s.sendOutOfStock(s"OutOfStock${i}").suspend(s3)
                }
            }
        }
    }

    def s6(s: S6): Done.type = {
        s match {
            case OKS(sid, x, s) => s.sendOKc("").suspend(s3)
            case DeclinedS(sid, x, s) => s.sendDeclinedc("").suspend(s3)
        }
    }
}


/* ... */

object oldP extends Actor("Processor") {

    def spawn(): Unit = {
        spawnAndRegister("localhost", 8888, "Proto1",
            "P", 6666, p1suspend)
    }

    def spawnAndRegister(apHost: String, apPort: Int, proto: Session.Global, r: Session.Role,
                         port: Int, f: P1Suspend => Done.type): Unit = {
        spawn(port)
        val g = (sid: Session.Sid) => f.apply(P1Suspend(sid, this))
        enqueueForSelectLoop(() => registerForPeers2(apHost, apPort, proto, r, port, g, Set("S", "C")))
    }

    def p1suspend(s: P1Suspend): Done.type = {
        s.suspend(p1)
    }

    var i = 0

    def p1(s: P1): Done.type = {
        s match {
            case BuyP(sid, x, s) => {
                i += 1
                if (i % 2 == 0) {
                    s.sendOK(s"OK${i}").suspend(p1)
                } else {
                    s.sendDeclined(s"Declined${i}").suspend(p1)
                }
            }
        }
    }
}


/* ... */

object oldC extends Actor("Customer") {

    def spawn(): Unit = {
        spawnAndRegister("localhost", 8888, "Proto1",
            "C", 5555, c1)
    }

    def spawnAndRegister(apHost: String, apPort: Int, proto: Session.Global, r: Session.Role,
                         port: Int, f: C1 => Done.type): Unit = {
        spawn(port)
        val g = (sid: Session.Sid) => f.apply(C1(sid, this))
        enqueueForSelectLoop(() => registerForPeers2(apHost, apPort, proto, r, port, g, Set("S", "P")))
    }

    def c1(s: C1): Done.type = {
        s.sendReqItems("").suspend(c2)
    }

    def c2(s: C2): Done.type = {
        s match {
            case ItemsC(sid, x, s) => c3(s)
        }
    }

    def c3(s: C3): Done.type = {
        Thread.sleep(2000)
        var s1 = s
        for (i <- Seq(1, 2, 3)) {
            s1 = s1.sendItemInfo("")
        }
        s1.sendCheckout("").suspend(c4)
    }

    def c4(s: C4): Done.type = {
        s match {
            case ProcessingC(sid, x, s) => s.suspend(c5)
            case OutOfStockC(sid, x, s) => c3(s)
        }
    }

    def c5(s: C5): Done.type = {
        s match {
            case OKcC(sid, x, s) => c3(s)
            case DeclinedcC(sid, x, s) => c3(s)
        }
    }
}

 */
