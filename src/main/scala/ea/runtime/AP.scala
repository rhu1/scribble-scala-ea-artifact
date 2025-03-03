package ea.runtime

import java.io.IOException
import java.net.SocketAddress
import java.nio.channels.{Selector, SocketChannel}
import scala.util.Try


object TestAP {
    def main(args: Array[String]): Unit = {
        println("hello")
        val p1 = new AP("Proto1", Set("A", "B"))
        p1.init(8888)
        p1.runSelectLoop()
    }
}


class AP(val proto: Session.Global, val rs: Set[Session.Role])
    extends EventServer(s"AP(${proto})") {

    //private val chi = collection.mutable.Map[Session.Role, Seq[(SocketChannel, Net.Host, Net.Port)]]()
    private val chi = collection.mutable.ListBuffer[(
        Session.Sid,
        collection.mutable.Map[Session.Role, (SocketChannel, Net.Host, Net.Port, Net.Liota)]
        )]()

    private val chi2 = collection.mutable.ListBuffer[(
        Session.Sid,
            collection.mutable.Map[Session.Role, (SocketChannel, Net.Host, Net.Port, Net.Liota)]
        )]()

    private val initSync = collection.mutable.Map[Session.Sid, collection.mutable.Set[Session.Role]]()

    /*override def init(port: Net.Port) {
        super.init(port)
        //rs.foreach(x => { chi(x) = List()})
    }*/

    //def acceptAndRegister(selector: Selector, key: SelectionKey): Option[SocketChannel] = {}

    def close(): Unit = {
        enqueueClose()
    }

    @throws[IOException]
    override def handleReadAndRegister(client: SocketChannel, selector: Selector, msg: String): Unit = {
        val socket = client

        //println(debugToString(s"[handleReadAndRegister] read: ${msg}"))
        val split = msg.split("_")
        val op = split(0)

        if (op == "IOTADONE") {

            //val pay = s"IOTADONE_${sid._1}_${sid._2}_${rr}_${iota}"
            val sid = (split(1), split(2).toInt)
            val rr = split(3)
            val iota = split(4)
            debugPrintln(s"Parsed: ${op} ${sid} ${rr} ${iota}")

            this.initSync(sid) += rr
            if (this.initSync(sid) == rs) {
                this.initSync -= sid

                val find = this.chi2.zipWithIndex.find(x => x._1._1 == sid).get
                this.chi2.remove(find._2)
                val conns = find._1._2
                for ((k, v) <- conns) {
                    val msg = s"APDONE_${sid._1}_${sid._2}_${k}_${v._4}"
                    write(v._1, msg)
                    v._1.close()
                }
            }

        } else if (op == "SERVER") {
            val proto1: Session.Global = split(1)
            val r: Session.Role = split(2)
            val host: Net.Host = split(3)
            val port: Net.Port = Try(split(4).toInt).toOption.getOrElse({
                errPrintln(s"Bad ${op}: Missing port arg")
                sys.exit(0)
            })
            val iota: String = split(5)
            debugPrintln(s"Parsed: ${op} ${proto1} ${r} ${host} ${port} ${iota}")

            /*val it = this.chi.iterator
            for ((sid, reqs) <- it) {

                if (reqs.keySet.union(Set(r)) == rs) {
                    debugPrintln(s"Init: ${this.chi}")

                    reqs += (r -> (socket, host, port, iota))

                    val mm = s"HEY_${sid._1}_${sid._2}_${r}_${iota}"  // Could batch with APCLIENT?  only for final role...
                    write(socket, mm)

                    var msg = ""
                    reqs.toSeq.sortBy(_._1).foreach(x => {
                        val rr = x._1
                        val h = x._2  // socket, host, port, iota
                        val s = h._1
                        msg = msg + "_" + rr + "_" + h._2 + "_" + h._3 + "_" + h._4
                    })

                    //write(socket, msg)
                    reqs.foreach(x => {
                        val h = x._2  // socket, host, port, iota
                        val s = h._1
                        val msg1 = s"APCLIENT_${h._4}_${sid._1}_${sid._2}_${x._1}${msg}"
                        write(s, msg1)
                    })

                    // XXX maybe not 0
                    this.chi2 += this.chi.head
                    this.chi.remove(0)
                    //it.remove ...

                    return  // !!!

                } else if (!reqs.contains(r)) {

                    val mm = s"HEY_${sid._1}_${sid._2}_${r}_${iota}"  // FIXME factor out with above
                    write(socket, mm)

                    //val tmp: Seq[(SocketChannel, Net.Host, Net.Port)] = reqs(r)
                    reqs += (r -> (socket, host, port, iota))
                    debugPrintln(s"Added to chi(${sid}): ${reqs}")

                    return  // !!!
                }
            }*/

            val find1 = this.chi.zipWithIndex.find(x => x._1._2.keySet.union(Set(r)) == rs)
            if (find1.isDefined) {
                val get = find1.get
                val (sid, reqs) = get._1
                debugPrintln(s"Init: ${this.chi}")
                reqs += (r -> (socket, host, port, iota))
                val mm = s"HEY_${sid._1}_${sid._2}_${r}_${iota}"  // Could batch with APCLIENT?  only for final role...
                write(socket, mm)
                var msg = ""
                reqs.toSeq.sortBy(_._1).foreach(x => {
                    val rr = x._1
                    val h = x._2  // socket, host, port, iota
                    val s = h._1
                    msg = msg + "_" + rr + "_" + h._2 + "_" + h._3 + "_" + h._4
                })

                //write(socket, msg)
                reqs.foreach(x => {
                    val h = x._2  // socket, host, port, iota
                    val s = h._1
                    val msg1 = s"APCLIENT_${h._4}_${sid._1}_${sid._2}_${x._1}${msg}"
                    write(s, msg1)
                })

                this.chi2 += this.chi(get._2)
                this.chi.remove(get._2)

                return  // !!!
            }

            val find2 = this.chi.zipWithIndex.find(x => !x._1._2.contains(r))
            if (find2.isDefined) {
                val get = find2.get
                val (sid, reqs) = get._1

                val mm = s"HEY_${sid._1}_${sid._2}_${r}_${iota}"  // FIXME factor out with above
                write(socket, mm)

                //val tmp: Seq[(SocketChannel, Net.Host, Net.Port)] = reqs(r)
                reqs += (r -> (socket, host, port, iota))
                debugPrintln(s"Added to chi(${sid}): ${reqs}")

                return  // !!!
            }

            val index = nextIndex()
            val sid: Session.Sid = (proto, index)  // !!!

            this.initSync(sid) = collection.mutable.Set[Session.Role]()

            val mm = s"HEY_${sid._1}_${sid._2}_${r}_${iota}"  // FIXME factor out
            write(socket, mm)

            val tmp = collection.mutable.Map[Session.Role, (SocketChannel, Net.Host, Net.Port, Net.Liota)](r -> (socket, host, port, iota))
            this.chi += ((sid, tmp))

        } else {
            debugPrintln(s"Unknown op: ${op}")
        }
    }

    override def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit = { }

    /* ... */

    private var counter = 0

    private def nextIndex(): Int = {
        this.counter += 1
        this.counter
    }
}
