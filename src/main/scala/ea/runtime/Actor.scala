package ea.runtime

import ea.runtime.Session.ActorState

import java.io.IOException
import java.nio.channels.{SelectionKey, Selector, SocketChannel}
import scala.collection.mutable.ListBuffer
import scala.concurrent.Promise
import scala.util.control.Exception.catching


abstract class Actor(val pid: Net.Pid) extends EventServer(s"Actor($pid)") {

    // For sessions (not APs)
    private val active = collection.mutable.Map[Session.Sid, collection.mutable.Set[Session.Role]]()
    private val sockets = collection.mutable.Map[(Session.Sid, Session.Role), SocketChannel]()  // Role is peer

    // sid, self role (dst), peer role (src)
    private val queues = collection.mutable.Map[(Session.Sid, Session.Role, Session.Role), ListBuffer[String]]()

    // sid, self role, peer role
    private val initHandlers = collection.mutable.Map[(Session.Sid, Session.Role), () => Done.type]()
    private val handlers = collection.mutable.Map[(Session.Sid, Session.Role, Session.Role), (String, String) => Done.type]()  // (Op, Pay)

    private val initSync = collection.mutable.Map[Net.Liota,
        (collection.mutable.Map[Session.Role, Promise[Unit]], SocketChannel, Session.Sid => Done.type)]()


    /* Apigen target */

    def enqueueRegisterForPeers
            [D <: Session.Data, S <: ActorState[Actor]]
            (apHost: String, apPort: Int, proto: Session.Global, r: Session.Role,
             port: Int, d: D, f: (D, S) => Done.type, m: Session.Sid => S, peers: Set[Session.Role]): Unit = {
        val g = (d: D, sid: Session.Sid) => {
            val s = m(sid)
            val done = f.apply(d, s)
            s.checkUsed()
            done
        }
        enqueueForSelectLoop(() => registerForPeers(apHost, apPort, proto, r, port, d, g, peers))
    }

    @throws[IOException]
    private def registerForPeers[D <: Session.Data]
            (apHost: Net.Host, apPort: Net.Port, proto: Session.Global,
            r: Session.Role, port: Net.Port, d: D, f: (D, Session.Sid) => Done.type,
            peers: Set[Session.Role]): Unit = {

        debugPrintln(s"Registering as $r server with AP: $proto")
        try {
            val apSocket = connectAndRegister(apHost, apPort).get

            debugPrintln(s"Connected AP: ${apSocket.getLocalAddress} -> ${apSocket.getRemoteAddress}")

            val i = nextIota()
            val ps: List[Session.Role] = peers.toList
            val ff = (x: Session.Sid) => f.apply(d, x)
            this.initSync(i) = (collection.mutable.Map(ps.map(x => x -> Promise[Unit]()): _*), apSocket, ff)

            val msg = s"SERVER_${proto}_${r}_localhost_${port}_$i"
            write(apSocket, msg)

            debugPrintln(s"Registered AP connected for READ: ${apSocket.getRemoteAddress}")

        } catch {
            case e: IOException => e.printStackTrace()
        }
    }

    // self = dst, peer = src
    def setHandler(sid: Session.Sid, self: Session.Role, peer: Session.Role, f: (String, String) => Done.type): Unit = {
        if (this.queues.contains((sid, self, peer))) {
            val q = this.queues((sid, self, peer))
            if (q.nonEmpty) {
                val h = q.head
                this.queues((sid, self, peer)) = q.tail
                val split = h.split("__")

                val op = split(0)
                val pay = if (split.length > 1) { split(1) } else { "" }  // cf. msg.substring in handleReadAndRegister (e.g. SEND case) works for empty pay
                f(op, pay)

            } else {
                this.handlers((sid, self, peer)) = f
            }
        } else {
            this.handlers((sid, self, peer)) = f
        }
    }

    @throws[IOException]
    def sendMessage(sid: Session.Sid, src: Session.Role, dst: Session.Role, op: String, pay: String): Unit = {
        debugPrintln(s"Sockets: $sockets")
        debugPrintln(s"Sending message to $sid[$dst]: $op($pay)")
        write(this.sockets((sid, dst)), s"SEND_${sid._1}_${sid._2}_${src}_${dst}_${op}_$pay")
    }

    // cf. s.finish -- without close, e.g., ChatServer handling multiple clients
    @throws[IOException]
    def finishAndClose[A <: Actor](s: Session.End[A]): Done.type =
        val done = s.finish()
        this.enqueueClose()
        done

    // r is self -- need to close conns to all _other_ r's
    def end(sid: Session.Sid, r: Session.Role): Unit = {
        debugPrintln(s"Ending $sid[$r]...")
        if (this.initHandlers.contains((sid, r))) {
            errPrintln(s"[WARNING] ending session for $r but init handlers non-empty: $sid")
            this.initHandlers -= ((sid, r))
        }

        val filt = this.handlers.filter(x => x._1._1 == sid && x._1._2 == r)
        if (this.initHandlers.contains((sid, r))
            || filt.nonEmpty) {
            errPrintln(s"[WARNING] ending session for $r but handlers non-empty: $sid")
            filt.foreach(x => this.handlers -= x._1)
        }
        this.active(sid) -= r

        if (this.active(sid).isEmpty) {
            this.sockets.filter(_._1._1 == sid).foreach(x => {
                // stops self comm actors from terminating
                catching(classOf[IOException]).opt(x._2.close())
            })
        }

        debugPrintln(s"...ended: $sid[$r]")
    }


    /* Event loop */

    @throws[IOException]
    override def handleReadAndRegister(client: SocketChannel, selector: Selector, msg: String): Unit = {
        val i = msg.indexOf('_')  // HEADER_...
        val kind = msg.substring(0, i)
        var sid: Session.Sid = null

        if (kind == "HEY") { // Could batch with APCLIENT ?
            val apSocket = client
            val mm = msg
            val split = mm.split("_")

            val p = split(1)
            val index = split(2).toInt // sid index
            val rr = split(3)
            val iota = split(4)

            val f = this.initSync(iota)._3
            sid = (p, index)
            val g = () => f.apply(sid)
            setInitHandler(sid, rr, g)

            apSocket.register(selector, SelectionKey.OP_READ, sid)

        } else if (kind == "APCLIENT") {
            // AP_Global_Int_host_port
            val jj = msg.indexOf('_', i + 1)
            val j = msg.indexOf('_', jj + 1)
            val k = msg.indexOf('_', j + 1)
            val l = msg.indexOf('_', k + 1)
            val ll = msg.indexOf('_', l + 1)
            val lll = msg.indexOf('_', ll + 1)
            val llll = msg.indexOf('_', lll + 1)
            val iota = msg.substring(i + 1, jj)
            sid = (msg.substring(jj + 1, j), msg.substring(j + 1, k).toInt)
            val rr = msg.substring(k + 1, l)

            val list = msg.substring(l + 1)
            debugPrintln(s"Parsed $kind: i=$iota, sid=$sid, rr=$rr, list=$list")

            val it = list.split("_").iterator
            while (it.hasNext) {
                val rrr = it.next()
                val host = it.next()
                val port = it.next().toInt
                val iotarrr = it.next
                if (rrr >= rr) { // !!!
                } else {
                    // connect to all lower; set each lower promise
                    doConnect(rr, host, port, sid, rrr, selector, iotarrr)
                    this.initSync(iota)._1(rrr).success(())
                }
            }

            checkIotaDone(client, iota, sid, rr, selector)

        } else if (kind == "CONNECT") {
            val j = msg.indexOf('_', i + 1)
            val k = msg.indexOf('_', j + 1)
            val l = msg.indexOf('_', k + 1)
            val ll = msg.indexOf('_', l + 1)
            sid = (msg.substring(i + 1, j), msg.substring(j + 1, k).toInt)
            val rrr = msg.substring(k + 1, l)
            val rr = msg.substring(l + 1, ll)
            val iota = msg.substring(ll + 1)
            debugPrintln(s"Parsed $kind: sid=$sid, rrr=$rrr, iota=$iota")

            // handle CONNECT
            client.register(selector, SelectionKey.OP_READ, sid)
            this.sockets((sid, rrr)) = client
            this.initSync(iota)._1(rrr).success(())

            checkIotaDone(this.initSync(iota)._2, iota, sid, rr, selector)

        } else if (kind == "APDONE") {

            val split = msg.split("_")
            sid = (split(1), split(2).toInt)
            val rr = split(3)
            val iota = split(4)
            debugPrintln(s"Parsed: op=$kind sid=$sid rr=$rr iota=$iota")

            this.initSync -= iota
            client.close() // AP client conn is per registerAP, i.e., separate for each session, and also for each role (in same session)

            val rs = this.active.getOrElseUpdate(sid, collection.mutable.Set())
            rs += rr
            dispatchInitHandler(sid, rr)

        } else if (kind == "SEND") { // sess receive
            // ..._Global_Int_pay
            val j = msg.indexOf('_', i + 1)
            val k = msg.indexOf('_', j + 1)
            val l = msg.indexOf('_', k + 1)
            val ll = msg.indexOf('_', l + 1)
            val lll = msg.indexOf('_', ll + 1)
            sid = (msg.substring(i + 1, j), msg.substring(j + 1, k).toInt)
            val src = msg.substring(k + 1, l)
            val rr = msg.substring(l + 1, ll)
            val op = msg.substring(ll + 1, lll)
            val pay = msg.substring(lll + 1)

            debugPrintln(s"Parsed session receive: sid=$sid, src=$src, rr=$rr, op=$op, pay=$pay")

            // handle SEND
            client.register(selector, SelectionKey.OP_READ, sid)

            dispatchHandler(sid, rr, src, op, pay)

        } else {
            errPrintln(s"Unknown kind: $kind")
        }
    }

    // self = dst, peer = src
    private def dispatchHandler(sid: Session.Sid, self: Session.Role, peer: Session.Role, op: String, pay: String): Unit = {
        if (!this.handlers.contains((sid, self, peer))) {
            val q = this.queues.getOrElseUpdate((sid, self, peer), ListBuffer())
            q += s"${op}__$pay"

        } else {
            val f = this.handlers(sid, self, peer)
            this.handlers -= ((sid, self, peer))
            f(op, pay)
        }
    }


    /* Initiation */

    @throws[IOException]
    private def doConnect(rr: Session.Role, host: Net.Host, port: Net.Port,
                          sid: Session.Sid, rrr: Session.Role, selector: Selector, iota: Net.Liota): Unit = {
        val opt = connectAndRegister(host, port)
        if (opt.isEmpty) {
            errPrintln(s"Couldn't connect to Actor $sid[$rrr]@$host:$port")
            return
        }

        val sSocket: SocketChannel = opt.get
        val msg1 = s"CONNECT_${sid._1}_${sid._2}_${rr}_${rrr}_$iota"
        write(sSocket, msg1)

        sSocket.register(selector, SelectionKey.OP_READ, sid)
        this.sockets((sid, rrr)) = sSocket
        debugPrintln(s"Connected Actor: sid=$sid, host=$host:$port")
    }

    private var iCounter = 0
    private def nextIota(): Net.Liota = {
        this.iCounter += 1
        s"${this.pid}:${this.iCounter}"
    }

    private val iotadones = collection.mutable.Set[(Session.Sid, Net.Liota)]()

    // Can be "concurrently" tried from APCLIENT and CONNECT if active not established in between
    private def checkIotaDone(client: SocketChannel, iota: Net.Liota, sid: Session.Sid, rr: Session.Role, selector: Selector): Unit = {

        if (this.iotadones.contains((sid, iota))) {
            return
        }

        if (this.active.contains(sid) && this.active(sid).contains(rr)) {
            return
        }

        if (this.initSync(iota)._1.forall(x => x._2.isCompleted)) {  // initSync roles map intialised with all peers as keys
            this.iotadones += ((sid, iota))
            val pay = s"IOTADONE_${sid._1}_${sid._2}_${rr}_$iota"
            write(client, pay)
        }
    }

    private def setInitHandler(sid: Session.Sid, r: Session.Role, f: () => Done.type): Unit = {
        this.initHandlers((sid, r)) = f
    }

    private def dispatchInitHandler(sid: Session.Sid, r: Session.Role): Unit = {
        val f = this.initHandlers(sid, r)
        this.initHandlers -= ((sid, r))
        f()
    }


    /* Serialization */

    //def serialize[T, D <: EADeserializer[T]](x: EASerializable[T, D]): String = x.toString
    def serializeString(x: String): String = x
    def serializeInt(x: Int): String = x.toString
    def serializeBoolean(x: Boolean): String = x.toString
    //def deserialize[T, D <: EADeserializer[T]](x: String, d: D): T = d.deserialize(x)
    def deserializeString(x: String): String = x
    def deserializeInt(x: String): Int = x.toInt
    def deserializeBoolean(x: String): Boolean = x.toBoolean
}

/*trait EASerializable[T, D <: EADeserializer[T]] {
    def serialize(): String
}

trait EADeserializer[T] {
    def deserialize(bs: String): T
}*/
