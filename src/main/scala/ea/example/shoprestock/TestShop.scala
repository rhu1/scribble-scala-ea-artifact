package ea.example.shoprestock

import ea.example.shoprestock.Shop.{Proto1, Proto2}
import ea.runtime.Net.Port
import ea.runtime.{Actor, Done, Session}

import java.net.SocketAddress
import java.util.concurrent.LinkedTransferQueue

object TestShop {

    val PORT_Proto1: Port = S.PORT_Proto1
    val PORT_Proto2: Port = S.PORT_Proto2

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val proto1 = new Proto1.Proto1
        proto1.spawn(PORT_Proto1)
        val proto2 = new Proto2.Proto2
        proto2.spawn(PORT_Proto2)
        Thread.sleep(1000)

        //C.debug = true
        //SF.debug = true

        SF.spawn()
        S.spawn()
        P.spawn()
        C.spawn()

        // Server protocol is non-terminating by default -- but actors can be closed from external
        for i <- 1 to 4 do println(s"Closed ${shutdown.take()}.")  // C, P, SF, S
        println(s"Closing ${proto1.nameToString()}...")
        proto1.close()
        println(s"Closing ${proto2.nameToString()}...")
        proto2.close()
    }
}


/* C */

class Data_C(var ids: collection.mutable.Seq[String]) extends Session.Data {
    var tmp: collection.mutable.Seq[String] = collection.mutable.Seq[String]()
}

object C extends Actor("Customer") with Proto1.ActorC {

    val PORT_C = 7777

    def spawn(): Unit =
        val d = new Data_C(collection.mutable.Seq())
        spawn(this.PORT_C)
        registerC(this.PORT_C, "localhost", 8888, d, cInit)

    def cInit(d: Data_C, s: Proto1.C1): Done.type =
        s.sendReqItems("()").suspend(d, c2)

    def c2(d: Data_C, s: Proto1.C2): Done.type = s match
        case Proto1.ItemsC(sid, role, x, s) =>
            d.ids ++= x.split("::")
            d.tmp ++= x.split("::")
            c3(d, s)

    def c3(d: Data_C, s: Proto1.C3): Done.type =
        if (d.tmp.nonEmpty) {
            val h = d.tmp.head
            d.tmp = d.tmp.tail
            s.sendGetItemInfo(h).suspend(d, c4)
        } else  {
            Thread.sleep(500)
            s.sendCheckout(d.ids(1)).suspend(d, c5)
        }

    def c4(d: Data_C, s: Proto1.C4): Done.type = s match
        case Proto1.ItemInfoC(sid, role, x, s) => c3(d, s)

    def c5(d: Data_C, s: Proto1.C5): Done.type = s match
        case Proto1.ProcessingC(sid, role, x, s) => s.suspend(d, c6)
        case Proto1.OutOfStockC(sid, role, x, s) => c3(d, s)

    def c6(d: Data_C, s: Proto1.C6): Done.type = s match
        case Proto1.OKcC(sid, role, x, s) => c3(d, s)
        case Proto1.DeclinedcC(sid, role, x, s) => c3(d, s)

    /* Close */

    override def afterClosed(): Unit = TestShop.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}


/* S */

//                                             id ->   (price, num)
class Data_S(val stock: collection.mutable.Map[String, (Int, Int)]) extends Session.Data {

    var ss1: Session.LinOption[Proto2.SS1] = Session.LinNone()
    var oos: String = ""

    def inStock(k: String): Boolean = this.stock.contains(k) && this.stock(k)._2 > 0
    def summary(): String = this.stock.keySet.mkString("::")
    def cost(k: String, n: Int): Int = this.stock(k)._1 * n

}

object S extends Actor("Shop") with Proto1.ActorS with Proto2.ActorSS {

    val PORT_Proto2: Port = 9999
    val PORT_Proto1: Port = 8888
    private val PORT_S: Port = 6666

    def spawn(): Unit = {
        val stock = collection.mutable.Map[String, (Int, Int)](
            "item1" -> (10, 1),
            "item2" -> (20, 0))
        val d = new Data_S(stock)
        spawn(this.PORT_S)
        registerSS(this.PORT_S, "localhost", PORT_Proto2, d, ss1Freeze)
    }

    /* Proto2 */

    private def ss1Freeze(d: Data_S, s: Proto2.SS1): Done.type =
        val (a, done) = Session.freeze(s,
            (sid: Session.Sid, role: Session.Role, a: Actor) => Proto2.SS1(sid, role, a))
        d.ss1 = a
        registerS(this.PORT_S, "localhost", PORT_Proto1, d, s1suspend)
        done

    def ss1(d: Data_S, s: Proto2.SS1): Done.type = {
        val ss1 = s.sendAddItem(s"add${d.oos}")
        val (p, x) = d.stock(d.oos)
        d.stock(d.oos) = (p, x + 2)
        println(s"${nameToString()} Restocked ${d.oos}: ${x+2} @ $p")
        val (a, done) = Session.freeze(ss1,
            (sid: Session.Sid, role: Session.Role, a: Actor) => Proto2.SS1(sid, role, a))
        d.ss1 = a
        done
    }

    def s3(d: Data_S, s: Proto1.S3): Done.type = {
        custReqHandler(d, s)
    }

    def paymentResponseHandler(d: Data_S, s: Proto1.S7): Done.type = {
        s match {
            case Proto1.OKS(sid, role, x, s) => s.sendOKc("delivery date").suspend(d, s3)
            case Proto1.DeclinedS(sid, role, x, s) => s.sendDeclinedc("insufficient funds").suspend(d, s3)
        }
    }

    /* Proto1 */

    def s1suspend(d: Data_S, s: Proto1.S1Suspend): Done.type =
        s.suspend(d, custReqHandler[Proto1.S1])

    def s1(d: Data_S, s: Proto1.S1): Done.type = custReqHandler(d, s)

    sealed trait S1orS3[T] {}
    object S1orS3 {
        implicit val T_S1: S1orS3[Proto1.S1] = new S1orS3[Proto1.S1] {}
        implicit val T_S3: S1orS3[Proto1.S3] = new S1orS3[Proto1.S3] {}
    }
    /*def isS1orS3[T: S1orS3](t: T): String = t match {
        case i: S1 => "%d is an Integer".format(i)
        case s: S3 => "%s is a String".format(s)
    }*/

    def custReqHandler[T: S1orS3](d: Data_S, s: T): Done.type = s match {
        case s: Proto1.S1 => s match {
            case Proto1.ReqItemsS(sid, role, x, s) =>
                s.sendItems(d.summary()).suspend(d, custReqHandler[Proto1.S3])
        }
        case s: Proto1.S3 => s match {
            case Proto1.GetItemInfoS(sid, role, x, s) =>
                val info = d.stock(x)
                nameToString()
                val pay = s"${info._1}@${info._2}"
                println(s"${nameToString()} GetItem: $x ${info._2} @ ${info._1}")
                s.sendItemInfo(pay).suspend(d, custReqHandler[Proto1.S3])
            case Proto1.CheckoutS(sid, role, x, s) =>
                println(s"${nameToString()} Checkout")
                if (d.inStock(x)) {
                    val pay = s"${d.cost(x, 1)}"
                    println(s"${nameToString()} In stock 1x $x: total price $pay")
                    val (p, a) = d.stock(x)
                    d.stock(x) = (p, a - 1)
                    s.sendProcessing(s"Processing$x")
                     .sendBuy(pay)
                     .suspend(d, paymentResponseHandler)
                } else {
                    val sus = s.sendOutOfStock(s"OutOfStock$x")
                    println(s"${nameToString()} Out of stock...")
                    d.oos = x
                    d.ss1 match {
                        case y: Session.LinSome[_] => Session.become(d, y, restockHandler)  // Proto2.SS1
                        case _: Session.LinNone => throw new RuntimeException("Missing frozen...")
                    }
                    sus.suspend(d, custReqHandler[Proto1.S3])
                }
        }
    }

    def restockHandler(d: Data_S, s: Proto2.SS1): Done.type = ss1(d, s)

    /* Close */

    override def afterClosed(): Unit = TestShop.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = {
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
    }
}


/* SF */

class Data_SF extends Session.Data {}

object SF extends Actor("Staff") with Proto2.ActorSF {

    private val PORT_SF = 4444

    def spawn(): Unit =
        spawn(this.PORT_SF)
        registerSF(this.PORT_SF, "localhost", TestShop.PORT_Proto2, new Data_SF, sf1suspend)

    def sf1suspend(d: Data_SF, s: Proto2.SF1Suspend): Done.type = s.suspend(d, sf1)

    def sf1(d: Data_SF, s: Proto2.SF1): Done.type = s match {
        case Proto2.AddItemSF(sid, role, x, s) => s.suspend(d, sf1)
        case Proto2.RemoveItemSF(sid, role, x, s) => s.suspend(d, sf1)
    }

    /* Close */

    override def afterClosed(): Unit = TestShop.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}


/* P */

class Data_P extends Session.Data {}

object P extends Actor("PaymentProcessor") with Proto1.ActorP {

    private val PORT_P: Port = 5555

    def spawn(): Unit =
        spawn(this.PORT_P)
        registerP(this.PORT_P, "localhost", 8888, new Data_P, p1suspend)

    def p1suspend(d: Data_P, s: Proto1.P1Suspend): Done.type =
        s.suspend(d, p1)

    def p1(d: Data_P, s: Proto1.P1): Done.type = s match {
        case Proto1.BuyP(sid, role, x, s) =>
            if (true) {
                s.sendOK("()").suspend(d, p1)
            } else {
                s.sendDeclined("()").suspend(d, p1)
            }
    }

    /* Close */

    override def afterClosed(): Unit = TestShop.shutdown.add(this.pid)

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        val a = addr.map(x => s"addr=${x.toString}").getOrElse("")
        val s = sid.map(x => s"sid=${x.toString}").getOrElse("")
        println(s"Channel exception: $a $s")
        cause.printStackTrace()
}

