package ea.runtime

import ea.runtime.Session.ActorState

import java.io.IOException
import java.nio.channels.{SelectionKey, Selector, SocketChannel}
import scala.collection.mutable.ListBuffer
import scala.concurrent.Promise
import scala.util.control.Exception.catching


/*object TestActor {
    var a: Actor = null

    def main(args: Array[String]): Unit = {
        println("hello")

        this.a = new Actor("TestA")
        a.init(8888)
        Util.spawn(() => a.runSelectLoop())

        TestActorB.init(7777)
        TestActorB.run()
    }

    object TestActorB extends Actor("TestB") {

        def run(): Unit = {

            val sid = ("Proto1", 1234)
            TestActor.a.initHandlers((sid, "A")) = () => { println("foooooooooo accept"); Done }
            this.initHandlers((sid, "B")) = () => { println("baaaaaaaaaar connect"); Done }

            TestActorB.connectAndRegister("localhost", 8888).get
            //doConnect("B", "localhost", 8888, sid, "A", this.fSelector.get, "TestB42")  // XXX fake iota doesn't work

            // FIXME selector
            //doConnect("B", "localhost", 8888, sid, "A", "TestB42")  // XXX fake iota doesn't work

            var i = 1
            while (true) {
                TestActor.a.handlers((sid, "B", "A")) = (op, pay) => { println("foooooooooo read"); Done }
                Thread.sleep(1000)
                TestActorB.sendMessage(sid, "B", "A", "foo", s"pay${i}")
                //TestActorB.sendMessage(sid, "B", "A", "foo", List(s"pay${i}"))
                i += 1
            }
        }
    }
}*/

/*object TestActorA extends Actor("TestA") {

    override def
}*/


/* ... */

//HERE -- add message queues, setHandler should dispatch if queue non-empty -- move dispatchInit back to APDONE

abstract class Actor(val pid: Net.Pid_C) extends EventServer(s"Actor(${pid})") {

    class Foo //private()

    // For sessions (not APs) -- !!! Actor abstraction could play multiple roles in same session (but Init disallows it)
    val active = collection.mutable.Map[(Session.Sid), collection.mutable.Set[Session.Role]]()
    val sockets = collection.mutable.Map[(Session.Sid, Session.Role), SocketChannel]()  // Role is peer

    // sid, self role (dst), peer role (src)
    val queues = collection.mutable.Map[(Session.Sid, Session.Role, Session.Role), ListBuffer[String]]()
    //val queues = collection.mutable.Map[(Session.Sid, Session.Role, Session.Role), ListBuffer[(String, List[Object])]]()
    ////val preHandlers = collection.mutable.Map[Net.Liota, Session.Sid => Done.type]()

    // sid, self role, peer role
    val initHandlers = collection.mutable.Map[(Session.Sid, Session.Role), () => Done.type]()
    val handlers = collection.mutable.Map[(Session.Sid, Session.Role, Session.Role), (String, String) => Done.type]()  // (Op, Pay)  // TODO pay types
    //val handlers = collection.mutable.Map[(Session.Sid, Session.Role, Session.Role), (String, List[Object]) => Done.type]()  // (Op, Pay)  // TODO pay types

    val initSync = collection.mutable.Map[Net.Liota, (collection.mutable.Map[Session.Role, Promise[Unit]], SocketChannel, Session.Sid => Done.type)]()
    //val initHelp = collection.mutable.Map[Session.Sid, Net.Liota]()

    val initHandlersFoo = collection.mutable.Map[(Session.Sid, Session.Role), () => _]()
    val handlersFoo = collection.mutable.Map[(Session.Sid, Session.Role, Session.Role), (String, String) => _]()  // (Op, Pay)  // TODO pay types
    val initSyncFoo = collection.mutable.Map[Net.Liota, (collection.mutable.Map[Session.Role, Promise[Unit]], SocketChannel, Session.Sid => _)]()


    /*trait Input {
      type Output
      val value: Output
    }
    def valueOf[T](v: T) = new Input {
        type Output = T
        val value: T = v
    }
    def dependentFunc(i: Input): i.Output = i.value*/

    /*class Foo {
        class Bar
    }
    val f1 = new Foo
    val b1: f1.Bar = new f1.Bar
    val f2 = new Foo
    //val b2: f2.Bar = new f1.Bar  // XXX
    val b2: f2.Bar = new f2.Bar*/

    /* ... */

    //def weaken[S <: Session.Linear](s: S): (S, Done.type) = (s, Done)

    /*// !!! deprecate
    def spawnAndRegister
            [D <: Session.Data, S <: ActorState[Actor]]
            (apHost: String, apPort: Int, proto: Session.Global, r: Session.Role,
             port: Int, d: D, f: (D, S) => Done.type, m: (Session.Sid) => S, peers: Set[Session.Role]): Unit = {
        spawn(port)
        enqueueRegisterForPeers(apHost, apPort, proto, r, port, d, f, m, peers)
    }*/

    def enqueueRegisterForPeers
            [D <: Session.Data, S <: ActorState[Actor]]
            (apHost: String, apPort: Int, proto: Session.Global, r: Session.Role,
             port: Int, d: D, f: (D, S) => Done.type, m: (Session.Sid) => S, peers: Set[Session.Role]): Unit = {
        val g = (d: D, sid: Session.Sid) => {
            //val s = proto1.A1(sid, this)
            val s = m(sid)
            val done = f.apply(d, s)
            s.checkUsed()
            done
        }
        enqueueForSelectLoop(() => registerForPeers(apHost, apPort, proto, r, port, d, g, peers))
    }

    /*def enqueueRegisterForPeersFoo[D <: Session.Data, A <: Actor, S <: ActorState[A]]
            (apHost: String, apPort: Int, proto: Session.Global, r: Session.Role,
             port: Int, d: D, f: (D, S) => _, m: (Session.Sid) => S,
             peers: Set[Session.Role]): Unit = {
        val g = (d: D, sid: Session.Sid) => {
            //val s = proto1.A1(sid, this)
            val s = m(sid)
            val done = f.apply(d, s)
            s.checkUsed()
            done
        }
        enqueueForSelectLoop(() => registerForPeersFoo(apHost, apPort, proto, r, port, d, g, peers))
    }*/

    // cf. s.finish -- without close, e.g., ChatServer handling multiple clients
    @throws[IOException]
    def finishAndClose[A <: Actor](s: Session.End[A]): Done.type = {
        val done = s.finish()
        this.enqueueClose()
        done
    }

    /*@throws[IOException]
    def finishAndCloseFoo[A <: Actor, T](s: Session.EndFoo[A, T]): T = {
        val done = s.finishFoo()
        this.close()
        //done
        //new Foo()
        s.getT()
    }*/


    @throws[IOException]
    def doConnect(rr: Session.Role, host: Net.Host, port: Net.Port,
                  //sid: Session.Sid, rrr: Session.Role, iota: Net.Liota): Unit = {
                  sid: Session.Sid, rrr: Session.Role, selector: Selector, iota: Net.Liota): Unit = {
        val opt = connectAndRegister(host, port)
        if (opt.isEmpty) {
            errPrintln(s"Couldn't connect to Actor ${sid}[${rrr}]@${host}:${port}")
            return
        }

        val sSocket: SocketChannel = opt.get
        val msg1 = s"CONNECT_${sid._1}_${sid._2}_${rr}_${rrr}_${iota}"
        write(sSocket, msg1)

        sSocket.register(selector, SelectionKey.OP_READ, sid)
        //registerWithSelector(sSocket, SelectionKey.OP_READ)
        this.sockets((sid, rrr)) = sSocket
        debugPrintln(s"Connected Actor: sid=${sid}, host=${host}:${port}")
    }

    // r is self -- need to close conns to all _other_ r's
    def end(sid: Session.Sid, r: Session.Role): Unit = {
        debugPrintln(s"Ending ${sid}[${r}]...")
        if (this.initHandlers.contains((sid, r))) {
            errPrintln(s"[WARNING] ending session for ${r} but init handlers non-empty: ${sid}")
            this.initHandlers -= ((sid, r))
        }

        val filt = this.handlers.filter(x => x._1._1 == sid && x._1._2 == r)  // x._1 == (sid, r) ?
        if (this.initHandlers.contains((sid, r))
            || filt.nonEmpty) {
            errPrintln(s"[WARNING] ending session for ${r} but handlers non-empty: ${sid}")
            filt.foreach(x => this.handlers -= x._1)
        }
        this.active(sid) -= r

        //catching(classOf[IOException]).opt(this.sockets(sid, r).close())  // XXX !!! r is self, not peers
        if (this.active(sid).isEmpty) {  // FIXME self comm? (multi role in same sess) -- XXX isEmpty guard can race condition with other role inits?
            this.sockets.filter(_._1._1 == sid).foreach(x => {
                // FIXME doesn't actually remove the sockets?...
                // FIXME ending one side (too early) can close all conns and break self comm? cf. TestProto01a ...
                // ... need to only close when all roles ended, but currently "all roles" unknown, leave to manual finishAndClose? ...
                // ... FIXME if so need Acotr.enqueueClose to override EventServer.enqueueClose to close all sockets ...

                // !!! stops self comm actors from terminating
                catching(classOf[IOException]).opt(x._2.close())

            })
        }

        debugPrintln(s"...ended: ${sid}[${r}]")
    }

    /*// !!! deprecate
    @throws[IOException]
    def sendMessage[T](sid: Session.Sid, dst: Session.Role, op: String, pay: T): Unit = {
        sendMessage(sid, dst, dst, op, pay)
    }*/

    @throws[IOException]
    //def sendMessage[T](sid: Session.Sid, src: Session.Role, dst: Session.Role, op: String, pay: T): Unit = {
    //def sendMessage[T <: Serializable](sid: Session.Sid, src: Session.Role, dst: Session.Role, op: String, pay: List[T]): Unit = {
    def sendMessage(sid: Session.Sid, src: Session.Role, dst: Session.Role, op: String, pay: String): Unit = {
        debugPrintln(s"Sockets: ${sockets}")
        debugPrintln(s"Sending message to ${sid}[${dst}]: ${op}(${pay})")

        // FIXME HERE IOException
        write(this.sockets((sid, dst)), s"SEND_${sid._1}_${sid._2}_${src}_${dst}_${op}_${pay}")

        /*// !!! FIXME HERE serialize pay (currently hardcoded String)
        val bs = serialise((sid._1, sid._2, src, dst, op, pay))
        val tmp = serialise1((sid._1, sid._2, src, dst, op, pay))
        println("fooooooooo: " + deserialise1(tmp))
        deserialise(bs)
        write(this.sockets((sid, dst)), s"SEND_${bs}")*/
    }

    //val canRun = Promise[Unit]()

    @throws[IOException]
    //@throws[SessionException]
    override def handleReadAndRegister(client: SocketChannel, selector: Selector, msg: String): Unit = {
        val i = msg.indexOf('_')  // HEADER_...
        val kind = msg.substring(0, i)
        var sid: Session.Sid = null

        //try {
            if (kind == "APCLIENT") {
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

                //val rrr = msg.substring(l+1, ll)
                //val host = msg.substring(ll + 1, lll)
                //val port = msg.substring(lll + 1).toInt
                //val iotarrr = msg.substring(llll + 1).toInt
                //println(debugToString(s"Parsed ${kind}: i=${iota}, sid=${sid}, rr=${rr}, rrr=${rrr}@host=${host}:${port}"))
                val list = msg.substring(l + 1)
                debugPrintln(s"Parsed ${kind}: i=${iota}, sid=${sid}, rr=${rr}, list=${list}")

                val it = list.split("_").iterator
                //for (x <- list.zip(0 until list.size) until max by 4) {
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

                // wait on all promises in order  // !!!
                //Future {
                /*for ((r1, p) <- this.initSync(iota)) {
                        val ff = p.future
                        Await.ready(ff, Duration.Inf)
                    }
                    dispatchHandler(sid)
                    println(debugToString(s"2222222 ${this.handlers}"))
                    println(debugToString(s"All connected: ${this.initSync(iota)}"))
                    this.canRun.success()*/

                //HERE maybe make serversocket accept in its own event loop separate from "main" actor? -- whole init needs to be, main actor just needs init event

                //}

                //client.close()

            } /*else if (kind == "APSERVER") {  // deprecated
                // AP_Global_Int
                val jj = msg.indexOf('_', i + 1)
                val j = msg.indexOf('_', jj + 1)
                val k = msg.indexOf('_', j + 1)
                val iota = (msg.substring(i + 1, jj).toInt)
                val sid = (msg.substring(i + 1, jj), msg.substring(j + 1, k).toInt)

                val rr = msg.substring(k+1)

                println(debugToString(s"Parsed ${kind}: iota=${iota}, sid=${sid}, r=${rr}"))

                //client.close()

            }*/
            else if (kind == "CONNECT") {
                val j = msg.indexOf('_', i + 1)
                val k = msg.indexOf('_', j + 1)
                val l = msg.indexOf('_', k + 1)
                val ll = msg.indexOf('_', l + 1)
                sid = (msg.substring(i + 1, j), msg.substring(j + 1, k).toInt)
                val rrr = msg.substring(k + 1, l)
                val rr = msg.substring(l + 1, ll)
                val iota = msg.substring(ll + 1)
                debugPrintln(s"Parsed ${kind}: sid=${sid}, rrr=${rrr}, iota=${iota}")

                // handle CONNECT
                client.register(selector, SelectionKey.OP_READ, sid)
                this.sockets((sid, rrr)) = client
                ////dispatchHandler(sid)
                // set promise
                this.initSync(iota)._1(rrr).success(())

                /*for ((r1, p) <- this.initSync(iota)) {
                    val ff = p.future
                    Await.ready(ff, Duration.Inf)
                }*/
                checkIotaDone(this.initSync(iota)._2, iota, sid, rr, selector)

            } else if (kind == "APDONE") {

                //val msg = s"APDONE_${sid._1}_${sid._2}_${rr}_${iota}"
                val split = msg.split("_")
                sid = (split(1), split(2).toInt)
                val rr = split(3)
                val iota = split(4)
                debugPrintln(s"Parsed: op=${kind} sid=${sid} rr=${rr} iota=${iota}")

                //catching(classOf[IOException]).opt(this.initSync(iota)._2.close())  // same as `client`
                this.initSync -= iota
                client.close() // AP client conn is per registerAP, i.e., separate for each session, and also for each role (in same session)

                // XXX sess msg could arrive before APDONE
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

                /*// !!! FIXME HERE deserialize pay (currently hardcoded String)
                val tuple = deserialise(msg.substring(i+1))
                val (p: Session.Global, id: Int, src: Session.Role, rr: Session.Role, op: String, pay: List[Object]) = tuple
                sid = (p, id)*/

                debugPrintln(s"Parsed session receive: sid=${sid}, src=${src}, rr=${rr}, op=${op}, pay=${pay}")

                // handle SEND
                client.register(selector, SelectionKey.OP_READ, sid)

                dispatchHandler(sid, rr, src, op, pay)
                //dispatchHandlerFoo(sid, rr, src, op, pay)

            } else if (kind == "HEY") { // Could batch with APCLIENT ?

                val apSocket = client
                val mm = msg
                val split = mm.split("_")

                val p = split(1)
                val index = split(2).toInt // sid index
                /*if (p != proto) {
                    errPrintln(debugToString(s"Proto mismatch: expected=${proto}, got=${p}"))
                    sys.exit(1)
                }*/
                val rr = split(3)
                val iota = split(4)

                val f = this.initSync(iota)._3
                sid = (p, index)
                val g = () => f.apply(sid)
                setInitHandler(sid, rr, g)

                apSocket.register(selector, SelectionKey.OP_READ, sid)
                //registerWithSelector(apSocket, SelectionKey.OP_READ)

            } else {
                errPrintln(s"Unknown kind: ${kind}")
            }
        /*} catch {
            case e: IOException =>
                if (sid != null) {
                    throw SessionException(cause=e)
                } else {
                    throw e
                }
        }*/
    }

    private val iotadones = collection.mutable.Set[(Session.Sid, Net.Liota)]()

    // Can be "concurrently" tried from APCLIENT and CONNECT if active not established in between
    def checkIotaDone(client: SocketChannel, iota: Net.Liota, sid: Session.Sid, rr: Session.Role, selector: Selector): Unit = {

        if (iotadones.contains((sid, iota))) {
            return
        }

        if (this.active.contains(sid) && this.active(sid).contains(rr)) {
            return
        }

        //val client = this.initSync(iota)._2
        if (this.initSync(iota)._1.forall(x => x._2.isCompleted)) {  // initSync roles map intialised with all peers as keys

            /*// !!! here because sess msg could arrive before APDONE
            val rs = this.active.getOrElseUpdate(sid, collection.mutable.Set())
            rs += rr
            dispatchInitHandler(sid, rr)  // XXX do "session handler reg" here (or perhaps before connecting?) but "session sends" after APDONE...*/

            iotadones += ((sid, iota))

            val pay = s"IOTADONE_${sid._1}_${sid._2}_${rr}_${iota}"
            write(client, pay)
            //client.register(selector, SelectionKey.OP_READ)
        }
    }

    /* ... */

    /*// FIXME deprecate
    @throws[IOException]
    def registerForPeers2(apHost: Net.Host, apPort: Net.Port, proto: Session.Global,
                          r: Session.Role, port: Net.Port, f: Session.Sid => Done.type,
                          peers: Set[Session.Role]): Unit = {
        val g = (d: Session.Data, sid: Session.Sid) => f(sid)
        registerForPeers(apHost, apPort, proto, r, port, null, g, peers)
    }*/

    /*@throws[IOException]
    def registerForPeersFoo[D <: Session.Data]
    (apHost: Net.Host, apPort: Net.Port, proto: Session.Global,
     r: Session.Role, port: Net.Port, d: D, f: (D, Session.Sid) => _,
     peers: Set[Session.Role]): Unit = {

        debugPrintln(s"Registering as ${r} server with AP: ${proto}")
        try {
            val apSocket = connect(apHost, apPort)
            //apSocket.register(this.fSelector.get, SelectionKey.OP_READ)
            registerWithSelector(apSocket, SelectionKey.OP_READ)

            debugPrintln(s"Connected AP: ${apSocket.getLocalAddress} -> ${apSocket.getRemoteAddress}")

            val i = nextIota()
            val ps: List[Session.Role] = peers.toList
            val ff = (x: Session.Sid) => f.apply(d, x)
            this.initSyncFoo(i) = (collection.mutable.Map(ps.map(x => x -> Promise[Unit]()): _*), apSocket, ff)

            val msg = s"SERVER_${proto}_${r}_localhost_${port}_${i}"  // HERE factor out message types and parsing
            write(apSocket, msg)

            debugPrintln(s"Registered AP connected for READ: ${apSocket.getRemoteAddress()}")

        } catch {
            case e: IOException => e.printStackTrace()
        }
    }*/

    @throws[IOException]
    def registerForPeers[D <: Session.Data]
            (apHost: Net.Host, apPort: Net.Port, proto: Session.Global,
             r: Session.Role, port: Net.Port, d: D, f: (D, Session.Sid) => Done.type,
             peers: Set[Session.Role]): Unit = {

        debugPrintln(s"Registering as ${r} server with AP: ${proto}")
        try {
            /*val opt = connectAndRegister(apHost, apPort)
            if (opt.isEmpty) {
                errPrintln(debugToString(s"Couldn't connect to AP: ${apHost}:${apPort}"))
                return
            }
            val apSocket = opt.get*/
            /*val apSocket = connect(apHost, apPort)
            ////val apSocket = SocketChannel.open(new InetSocketAddress(apHost, apPort))
            ////apSocket.configureBlocking(false)
            //apSocket.register(this.fSelector.get, SelectionKey.OP_READ)
            registerWithSelector(apSocket, SelectionKey.OP_READ)*/
            val apSocket = connectAndRegister(apHost, apPort).get

            debugPrintln(s"Connected AP: ${apSocket.getLocalAddress} -> ${apSocket.getRemoteAddress}")

            val i = nextIota()
            val ps: List[Session.Role] = peers.toList
            val ff = (x: Session.Sid) => f.apply(d, x)
            this.initSync(i) = (collection.mutable.Map(ps.map(x => x -> Promise[Unit]()): _*), apSocket, ff)

            val msg = s"SERVER_${proto}_${r}_localhost_${port}_${i}"  // HERE factor out message types and parsing
            write(apSocket, msg)

            /*// HERE synchronous with AP? get back sid
            val opt = read(apSocket)
            if (opt.isEmpty) {
                errPrintln(debugToString(s"Couldn't read HEY"))
                sys.exit(1)
            }
            val mm = opt.get
            val split = mm.split("_")
            val hdr = split(0)
            if (hdr != "HEY") {
                errPrintln(debugToString(s"Wrong header, expected=HEY, got=${hdr}"))
                sys.exit(1)
            }

            val p = split(1)
            val index = split(2).toInt
            if (p != proto) {
                errPrintln(debugToString(s"Proto mismatch: expected=${proto}, got=${p}"))
                sys.exit(1)
            }

            //setPreHandler(i, f)
            val sid = (p, index)
            val g = () => f.apply(sid)
            setInitHandler(sid, r, g)*/

            debugPrintln(s"Registered AP connected for READ: ${apSocket.getRemoteAddress()}")

        } catch {
            case e: IOException => e.printStackTrace()
        }
    }


    /* ... */

    /*def setPreHandler(i: Net.Liota, f: Session.Sid => Done.type): Unit = {
        this.preHandlers += (i -> f)
    }

    def dispatchPreHandler(i: Net.Liota, sid: Session.Sid): Unit = {
        val f = this.preHandlers(i)
        this.handlers -= i
        f(sid)
    }*/

    private var iCounter = 0

    def nextIota(): Net.Liota = {
        this.iCounter += 1
        s"${this.pid}:${this.iCounter}"
    }

    //private def nextIota(): Net.Liota = Helper.nextIota()

    def setInitHandler(sid: Session.Sid, r: Session.Role, f: () => Done.type): Unit = {
        this.initHandlers((sid, r)) = f
    }

    def dispatchInitHandler(sid: Session.Sid, r: Session.Role): Unit = {

        //if (this.queues.con)

        val f = this.initHandlers(sid, r)
        this.initHandlers -= ((sid, r))
        f()
    }

    /*def setInitHandlerFoo(sid: Session.Sid, r: Session.Role, f: () => this.Foo): Unit = {
        this.initHandlersFoo((sid, r)) = f
    }

    def dispatchInitHandlerFoo(sid: Session.Sid, r: Session.Role): Unit = {

        //if (this.queues.con)

        val f = this.initHandlersFoo(sid, r)
        this.initHandlersFoo -= ((sid, r))
        f()
    }*/

    /*// deprecate
    def setHandler(sid: Session.Sid, r: Session.Role, f: (String, String) => Done.type): Unit = {
        this.handlers((sid, r, r)) = f
    }*/

    // self = dst, peer = src
    def setHandler(sid: Session.Sid, self: Session.Role, peer: Session.Role, f: (String, String) => Done.type): Unit = {
    //def setHandler(sid: Session.Sid, self: Session.Role, peer: Session.Role, f: (String, List[Object]) => Done.type): Unit = {

            if (this.queues.contains((sid, self, peer))) {
            val q = this.queues((sid, self, peer))
            if (q.nonEmpty) {
                val h = q.head
                this.queues((sid, self, peer)) = q.tail
                val split = h.split("__")

                val op = split(0)

                //val pay = split(1)
                val pay = if (split.length > 1) { split(1) } else { "" }  // cf. msg.substring in handleReadAndRegister (e.g. SEND case) works for empty pay

                /*val op = h._1
                val pay = h._2*/

                f(op, pay)

            } else {
                this.handlers((sid, self, peer)) = f
            }
        } else {
            this.handlers((sid, self, peer)) = f
        }
    }

    /*// deprecate
    def dispatchHandler(sid: Session.Sid, r: Session.Role, op: String, pay: String): Unit = {
        val f = this.handlers(sid, r, r)
        this.handlers -= ((sid, r, r))
        f(op, pay)
    }*/

    // self = dst, peer = src
    def dispatchHandler(sid: Session.Sid, self: Session.Role, peer: Session.Role, op: String, pay: String): Unit = {
    //def dispatchHandler(sid: Session.Sid, self: Session.Role, peer: Session.Role, op: String, pay: List[Object]): Unit = {
        if (!this.handlers.contains((sid, self, peer))) {
            val q = this.queues.getOrElseUpdate((sid, self, peer), ListBuffer())
            q += s"${op}__${pay}"
            //q += ((op, pay))

        } else {
            val f = this.handlers(sid, self, peer)
            this.handlers -= ((sid, self, peer))
            f(op, pay)
        }
    }

    /*// self = dst, peer = src
    def setHandlerFoo(sid: Session.Sid, self: Session.Role, peer: Session.Role, f: (String, String) => this.Foo): Unit = {

        if (this.queues.contains((sid, self, peer))) {
            val q = this.queues((sid, self, peer))
            if (q.nonEmpty) {
                val h = q.head
                this.queues((sid, self, peer)) = q.tail
                /*val split = h.split("__")
                val op = split(0)
                val pay = split(1)*/
                val op = h._1
                val pay = h._2
                f(op, pay)

            } else {
                this.handlersFoo((sid, self, peer)) = f
            }
        } else {
            this.handlersFoo((sid, self, peer)) = f
        }
    }

    // self = dst, peer = src
    def dispatchHandlerFoo(sid: Session.Sid, self: Session.Role, peer: Session.Role, op: String, pay: String): Unit = {

        if (!this.handlersFoo.contains((sid, self, peer))) {
            val q = this.queues.getOrElseUpdate((sid, self, peer), ListBuffer())
            q += s"${op}__${pay}"

        } else {
            val f = this.handlersFoo(sid, self, peer)
            this.handlersFoo -= ((sid, self, peer))
            f(op, pay)
        }
    }*/

    def serialize[T, D <: EADeserializer[T]](x: EASerializable[T, D]): String = x.toString
    def serializeString(x: String): String = x
    def serializeInt(x: Int): String = x.toString
    def serializeBoolean(x: Boolean): String = x.toString
    def deserialize[T, D <: EADeserializer[T]](x: String, d: D): T = d.deserialize(x)
    def deserializeString(x: String): String = x
    def deserializeInt(x: String): Int = x.toInt
    def deserializeBoolean(x: String): Boolean = x.toBoolean
}

trait EASerializable[T, D <: EADeserializer[T]] {
    def serialize(): String
}

trait EADeserializer[T] {
    def deserialize(bs: String): T
}


/*object Helper {

    private var iCounter = 0

    // !!! FIXME use pid to make globally unique
    def nextIota(): Net.Liota = {
        this.iCounter += 1
        this.iCounter
    }
}*/