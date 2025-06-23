package ea.example.shoprestock

import ea.runtime.{Actor, Done, Session}
import ea.example.shoprestock.Shop.{Proto1, Proto2}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestShop {

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()
    
    def main(args: Array[String]): Unit = {
        println("Hello")

        val proto1 = new Proto1.Proto1
        proto1.spawn(8888)
        val proto2 = new Proto2.Proto2
        proto2.spawn(9999)
        Thread.sleep(1000)

        //C.debug = true
        //SF.debug = true

        SF.spawn()
        S.spawn()
        //Thread.sleep(1000)  // !!! make sure Staff established

        P.spawn()
        C.spawn()
        
        // Only waiting for Shop -- but fake, Shop protocol is non-terminating
        println(s"Closed ${shutdown.take()}.")
        println(s"Closing ${proto1.nameToString()}...")
        proto1.close()
        println(s"Closing ${proto2.nameToString()}...")
        proto2.close()
    }
}


/* ... */

class DataC(var ids: collection.mutable.Seq[String]) extends Session.Data {
    var tmp = collection.mutable.Seq[String]()
}

object C extends Actor("Customer") with Proto1.ActorC {

    val port = 7777

    def spawn(): Unit = {
        val d = new DataC(collection.mutable.Seq())
        spawn(this.port)
        registerC(this.port, "localhost", 8888, d, cInit)
    }

    def cInit(d: DataC, s: Proto1.C1): Done.type = {
        s.sendReqItems("()").suspend(d, c2)
    }

    def c2(d: DataC, s: Proto1.C2): Done.type = {
        s match {
            case Proto1.ItemsC(sid, role, x, s) =>
                d.ids ++= x.split("::")
                d.tmp ++= x.split("::")
                c3(d, s)
        }
    }

    def c3(d: DataC, s: Proto1.C3): Done.type = {
        if (d.tmp.nonEmpty) {
            val h = d.tmp.head
            d.tmp = d.tmp.tail
            s.sendGetItemInfo(h).suspend(d, c4)
        } else  {
            Thread.sleep(500)
            s.sendCheckout(d.ids(1)).suspend(d, c5)
        }
    }

    def c4(d: DataC, s: Proto1.C4): Done.type = {
        s match {
            case Proto1.ItemInfoC(sid, role, x, s) => c3(d, s)
        }
    }

    def c5(d: DataC, s: Proto1.C5): Done.type = {
        s match {
            case Proto1.ProcessingC(sid, role, x, s) => s.suspend(d, c6)
            case Proto1.OutOfStockC(sid, role, x, s) => c3(d, s)
        }
    }

    def c6(d: DataC, s: Proto1.C6): Done.type = {
        s match {
            case Proto1.OKcC(sid, role, x, s) => c3(d, s)
            case Proto1.DeclinedcC(sid, role, x, s) => c3(d, s)
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* ... */

// id -> (price, num)
class DataS(val stock: collection.mutable.Map[String, (Int, Int)]) extends Session.Data {

    var ss1: Session.LinOption[Proto2.SS1] = Session.LinNone()

    def inStock(k: String): Boolean = this.stock.contains(k) && this.stock(k)._2 > 0
    def summary(): String = this.stock.keySet.mkString("::")
    def cost(k: String, n: Int): Int = this.stock(k)._1 * n

    var oos: String = ""
}

//trait SS extends ActorS with ShopProto2.ActorSS
//object S extends Actor("Shop") with SS {

object S extends Actor("Shop") with Proto1.ActorS with Proto2.ActorSS {

val port = 6666

    def spawn(): Unit = {
        val stock = collection.mutable.Map[String, (Int, Int)](
            "item1" -> (10, 1),
            "item2" -> (20, 0))
        val d = new DataS(stock)
        spawn(this.port)
        registerSS(this.port, "localhost", 9999, d, ss1Cache)
        //register(this.port, "localhost", 8888, d, s1suspend)  // !!! Move to ss1 ?
    }

    def ss1Cache(d: DataS, s: Proto2.SS1): Done.type = {
        //val done = Session.cache(s, (sid: Session.Sid, a: Actor) => SS1(sid, a), (a: Some[SS1]) => d.ss1 = a)  // s.cache((a: Some[SS1]) => d.ss1 = a)
        val (a, done) = Session.freeze(s, (sid: Session.Sid, role: Session.Role, a: Actor) => Proto2.SS1(sid, role, a))
        d.ss1 = a
        registerS(this.port, "localhost", 8888, d, s1suspend)
        done
    }

    def s1suspend(d: DataS, s: Proto1.S1Suspend): Done.type = {
        //s.suspend(d, s1)
        s.suspend(d, custReqHandler[Proto1.S1])
    }

    def s1(d: DataS, s: Proto1.S1): Done.type = {
        custReqHandler(d, s)
    }

    sealed trait S1orS3[T] {}
    object S1orS3 {
        implicit val T_S1: S1orS3[Proto1.S1] = new S1orS3[Proto1.S1] {}
        implicit val T_S3: S1orS3[Proto1.S3] = new S1orS3[Proto1.S3] {}
    }
    /*def isS1orS3[T: S1orS3](t: T): String = t match {
        case i: S1 => "%d is an Integer".format(i)
        case s: S3 => "%s is a String".format(s)
    }*/

    // !!! S1orS3
    def custReqHandler[T: S1orS3](d: DataS, s: T): Done.type = {
        s match {
            case s: Proto1.S1 =>
                println("S1")
                s match {
                    case Proto1.ReqItemsS(sid, role, x, s) =>
                        s.sendItems(d.summary())
                         //.suspend(d, s3)
                         .suspend(d, custReqHandler[Proto1.S3])
                }
            case s: Proto1.S3 =>
                println("S3")
                s match {
                    case Proto1.GetItemInfoS(sid, role, x, s) =>
                        val info = d.stock(x)
                        val pay = s"${info._1}@${info._2}"
                        //s.sendItemInfo(pay).suspend(d, s3)
                        println(s"S: GetItem: $x $pay")
                        s.sendItemInfo(pay).suspend(d, custReqHandler[Proto1.S3])
                    case Proto1.CheckoutS(sid, role, x, s) =>
                        println(s"S: Checkout")
                        if (d.inStock(x)) {
                            val pay = s"${d.cost(x, 1)}"
                            println(s"S: In stock: $x $pay")
                            val (p, a) = d.stock(x)
                            d.stock(x) = (p, a - 1)
                            s.sendProcessing(s"Processing${x}")
                             .sendBuy(pay)
                             .suspend(d, paymentResponseHandler)
                        } else {
                            val sus = s.sendOutOfStock(s"OutOfStock${x}")
                            println(s"S: Out of stock")
                            d.oos = x
                            // !!! assumes staff established -- Option vs. affine
                            d.ss1 match {
                                // !!! type case
                                case y: Session.LinSome[_] =>  // Proto2.SS1
                                    Session.become(d, y, restockHandler)  // [[d.ss1]].become(d, ss1)
                                case _: Session.LinNone =>
                            }
                            sus.suspend(d, custReqHandler[Proto1.S3])
                        }
                }
        }
    }

    def restockHandler(d: DataS, s: Proto2.SS1): Done.type = {
        ss1(d, s)
    }

    def ss1(d: DataS, s: Proto2.SS1): Done.type = {
        println(s"become: add ${d.oos}")
        //ss1Cache(d, s.sendAddItem(s"add${d.oos}"))  // XXX
        val ss1 = s.sendAddItem(s"add${d.oos}")
        val (p, x) = d.stock(d.oos)
        d.stock(d.oos) = (p, x + 2)
        val (a, done) = Session.freeze(ss1, (sid: Session.Sid, role: Session.Role, a: Actor) => Proto2.SS1(sid, role, a))
        d.ss1 = a
        //registerS(this.port, "localhost", 8888, d, s1suspend)  // XXX
        done
    }

    def s3(d: DataS, s: Proto1.S3): Done.type = {
        custReqHandler(d, s)
    }

    def paymentResponseHandler(d: DataS, s: Proto1.S7): Done.type = {
        s match {
            case Proto1.OKS(sid, role, x, s) => s.sendOKc("delivery date").suspend(d, s3)
            case Proto1.DeclinedS(sid, role, x, s) => s.sendDeclinedc("insufficient funds").suspend(d, s3)
        }
    }

    override def afterClosed(): Unit =
        TestShop.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* ... */

class DataSF extends Session.Data

object SF extends Actor("Staff") with Proto2.ActorSF {

    val port = 4444

    def spawn(): Unit = {
        spawn(this.port)
        registerSF(this.port, "localhost", 9999, new DataSF, sf1suspend)
    }

    def sf1suspend(d: DataSF, s: Proto2.SF1Suspend): Done.type = {
        s.suspend(d, sf1)
    }

    def sf1(d: DataSF, s: Proto2.SF1): Done.type = {
        s match {
            case Proto2.AddItemSF(sid, role, x, s) => s.suspend(d, sf1)
            case Proto2.RemoveItemSF(sid, role, x, s) => s.suspend(d, sf1)
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}


/* ... */

class DataP extends Session.Data

object P extends Actor("PaymentProcessor") with Proto1.ActorP {

    val port = 5555

    def spawn(): Unit = {
        spawn(this.port)
        registerP(this.port, "localhost", 8888, new DataP, p1suspend)
    }

    def p1suspend(d: DataP, s: Proto1.P1Suspend): Done.type = {
        s.suspend(d, p1)
    }

    def p1(d: DataP, s: Proto1.P1): Done.type = {
        s match {
            case Proto1.BuyP(sid, role, x, s) =>
                if (true) {
                    s.sendOK("()").suspend(d, p1)
                } else {
                    s.sendDeclined("()").suspend(d, p1)
                }
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: ${a} ${s}")
        cause.printStackTrace()
    }
}

