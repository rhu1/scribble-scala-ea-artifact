package ea.runtime

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException, ObjectInputStream, ObjectOutputStream}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel, SocketChannel}
import java.nio.charset.StandardCharsets
import java.util.ConcurrentModificationException
import scala.collection.mutable.ListBuffer


object TestEventServer {

    def main(args: Array[String]): Unit = {
        println("hello")

        TestEventServerA.init(8888)
        Util.spawn(() => TestEventServerA.runSelectLoop())

        TestEventServerB.init(7777)
        TestEventServerB.run()
    }
}

object TestEventServerB extends EventServer("(TestB)") {

    def run(): Unit = {
        val a = TestEventServerB.connectAndRegister("localhost", 8888).get
        var i = 1
        while (true) {
            TestEventServerB.write(a, s"msg${i}")
            i += 1
            Thread.sleep(1000)
        }
    }

    override def handleReadAndRegister(client: SocketChannel, selector: Selector, msg: String): Unit = ???
}

object TestEventServerA extends EventServer("(TestA)") {

    override def handleReadAndRegister(client: SocketChannel, selector: Selector, msg: String): Unit = {
        val opt = read(client)
        if (opt.isEmpty) {
            errPrintln("Read None")
            return
        }
        val ms = opt
        //(debugToString andThen println)(msg)
        debugPrintln(s"!!! ${ms}")
    }
}


/* ... */

abstract class EventServer[Id](val name: String) extends DebugPrinter {

    private var selecting = false
    private var fServerSocket: Option[ServerSocketChannel] = None
    var fSelector: Option[Selector] = None

    //private val sockets = collection.mutable.Map[Id, SocketChannel]()

    def spawn(port: Int): Unit = {
        init(port)
        Util.spawn(() => runSelectLoop())
        //import scala.concurrent.ExecutionContext.Implicits.global
        //Future { runSelectLoop() }  // XXX Terminates too early?
    }

    // ...integrate into run?
    // Pre: !this.selecting, this.serverSocket == None, this.selector == None
    @throws[IOException]
    def init(port: Int): Unit = {
        if (this.selecting) {  // Implies this.selector and this.serverSocket not None (via run)
            errPrintln("Already selecting, cannot init again")
            return
        }
        val selector = Selector.open
        val serverSocket = ServerSocketChannel.open
        serverSocket.bind(new InetSocketAddress ("localhost", port))
        serverSocket.configureBlocking(false)
        serverSocket.register(selector, SelectionKey.OP_ACCEPT)
        this.fSelector = Some(selector)
        this.fServerSocket = Some(serverSocket)
        debugPrintln(s"Server bound: ${port}")
    }

    // FIXME enqueue?
    // Post: !this.selecting, this.serverSocket == None, this.selector == None
    @throws[IOException]
    def close(): Unit = {
        debugPrintln("stopping...")
        this.selecting = false
        try {
            this.fSelector.foreach(_.close)
        } finally {
            try {
                this.fServerSocket.foreach(_.close)
            } finally {
                this.fSelector = None
                this.fServerSocket = None
            }
        }
    }

    private val lock = new Object()
    private val queued = new ListBuffer[() => Unit]()

    def enqueueForSelectLoop(f: () => Unit): Unit = {
        this.lock.synchronized {
            this.queued += f
            //this.lock.notifyAll()
        }
        if (this.fSelector.isDefined) {
            this.fSelector.get.wakeup()
        }
    }

    // Pre: !this.selecting, this.serverSocket == Some, this.selector == Some
    @throws[IOException]
    def runSelectLoop(): Unit = {
        if (this.selecting) {
            errPrintln("Already selecting")
            return
        } else if (this.fServerSocket.isEmpty) {
            errPrintln("ServerSocket not open")
            return
        } else if (this.fSelector.isEmpty) {
            errPrintln("No Selector")
            return
        }

        this.selecting = true;
        //val serverSocket = this.serverSocket.get
        val selector = this.fSelector.get
        while (this.selecting) {

            this.lock.synchronized {
                while (this.queued.nonEmpty) {
                    val next = this.queued.remove(0)
                    next.apply()
                }
            }

            debugPrintln("Selecting...")
            selector.select()

            debugPrintln(s"...selected: ${selector.selectedKeys.toString}")
            val keys = selector.selectedKeys.iterator
            while (keys.hasNext) {

                // !!! concurrent modif? probably close? e.g., two sessions (e.g., Gen07), close in handler for one session closes all, but other session could still be in remaining while-loop keys (e.g., concurrent EOF?)
                // cf. CancelledKey
                // FIXME close should be enqueued?
                try {
                    val key = keys.next()
                    keys.remove()

                    // HERE TODO CancelledKey exception (e.g., peer closed)

                    if (key.isAcceptable) {
                        handleAcceptAndRegister(selector, key)
                    }
                    if (key.isReadable) {
                        handleReadAndRegister(selector, key)
                    }
                } catch {
                    case e: ConcurrentModificationException =>
                        println(debugToString("Caught..."))
                        e.printStackTrace()
                        //close()
                        enqueueForSelectLoop(() => close())
                        debugPrintln("Force stopped.")
                        return
                }
            }
        }
        debugPrintln("stopped.")
    }

    @throws[IOException]
    def handleAcceptAndRegister(selector: Selector, key: SelectionKey): Option[SocketChannel] = {
        val serverSocket = key.channel.asInstanceOf[ServerSocketChannel]
        val client = accept(serverSocket)
        client.register(selector, SelectionKey.OP_READ)
        debugPrintln(s"Registered accepted for READ: ${client.getRemoteAddress()}")
        Some(client)
    }

    @throws[IOException]
    def handleReadAndRegister(selector: Selector, key: SelectionKey): Unit = {
        val client = key.channel.asInstanceOf[SocketChannel]
        //val r = client.read(buffer)
        val opt = read(client)
        if (opt.isEmpty) {
            //if (r == -1) { //|| new String(buffer.array).trim == POISON_PILL) {
            client.close() // CHECKME: this.close ?
            debugPrintln("Not accepting client messages anymore")

        } else {
            val ms = opt
            ms.foreach(x => handleReadAndRegister(client, selector, x))
        }
    }

    // cf. val client = key.channel.asInstanceOf[SocketChannel]
    @throws[IOException]
    //def handleReadAndRegister(selector: Selector, key: SelectionKey): Unit
    def handleReadAndRegister(client: SocketChannel, selector: Selector, msg: String): Unit

    @throws[IOException]
    def connectAndRegister(host: Net.Host, port: Net.Port): Option[SocketChannel] = {
        if (this.fSelector.isEmpty) {
            errPrintln("No selector")
            None
        } else {
            val selector = this.fSelector.get
            val c = connect(host, port)
            c.register(selector, SelectionKey.OP_READ)
            debugPrintln("Registered connected for READ: ${c.getRemoteAddress()}")
            Some(c)
        }
    }


    /* ... */

    /*def addSocket(id: Id, socket: SocketChannel): Unit = {
        this.sockets(id) = socket
    }

    def getSocket(id: Id): Option[SocketChannel] = {
        if (!this.sockets.contains(id)) {
            None
        } else {
            Some(this.sockets(id))
        }
    }*/


    /* Channel I/O -- independent of event loop */

    @throws[IOException]
    def connect(host: Net.Host, port: Net.Port): SocketChannel = {
        //println(s"${name} connecting... ${port}")
        val sSocket = SocketChannel.open(new InetSocketAddress(host, port))
        sSocket.configureBlocking(false)
        debugPrintln(s"Connected Actor: ${sSocket.getRemoteAddress()}")
        sSocket
    }

    @throws[IOException]
    def accept(/*selector: Selector, */serverSocket: ServerSocketChannel): SocketChannel = {
        val client = serverSocket.accept()
        client.configureBlocking(false)
        //client.register(selector, SelectionKey.OP_READ)
        debugPrintln(s"Accepted: ${client.getRemoteAddress()}")
        client
    }

    /*
    //def serialise(value: Serializable): Array[Byte] = {
    def serialise(value: Serializable): String = {
        val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
        val oos = new ObjectOutputStream(stream)
        oos.writeObject(value)
        oos.close()
        val bs = stream.toByteArray
        new String(bs, StandardCharsets.UTF_8)
    }

    //def deserialise(bytes: Array[Byte]): Serializable = {
    def deserialise(bs: String): Serializable = {
        val bytes = bs.getBytes(StandardCharsets.UTF_8)
        val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
        val value = ois.readObject.asInstanceOf[Serializable]
        ois.close()
        value
    }

    def serialise1(value: Serializable): Array[Byte] = {
        val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
        val oos = new ObjectOutputStream(stream)
        oos.writeObject(value)
        oos.close()
        stream.toByteArray
    }
    */

    def deserialise1(bytes: Array[Byte]): Serializable = {
        val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
        val value = ois.readObject.asInstanceOf[Serializable]
        ois.close()
        value
    }

    @throws[IOException]
    def write(c: SocketChannel, pay: String): Unit = {

        // !!! val tmp = catching(classOf[IOException]).either(c.getRemoteAddress)
        val addr = c.getRemoteAddress()

        val msg = s"${pay}."
        val buffer = ByteBuffer.wrap(msg.getBytes)
        debugPrintln(s"Writing ${buffer.remaining()} bytes to ${addr}...")
        c.write(buffer)
        debugPrintln(s"...written to ${addr}: ${msg}")
    }

    /*def write[T <: Serializable](c: SocketChannel, pay: List[T]): Unit = {
        //pay.map(x => serialise(x)).fold("", (x, y) => x+y)
        write(c, serialise(pay))
    }*/

    type RemoteAddressString = String
    val buffers: collection.mutable.Map[RemoteAddressString, ByteBuffer] = collection.mutable.Map()

    // !!! exceptions vs. Option, Try, Either, ... -- scala 3 (using CanThrow[...])
    @throws[IOException]
    def read(c: SocketChannel): Seq[String] = {
        val k = c.getRemoteAddress().toString
        val buffer = this.buffers.getOrElseUpdate(k, ByteBuffer.allocate(2048))  // !!!
        val r = c.read(buffer)
        if (r == -1) {  // !!! CHECKME cf. above getRemoteAddress -- cf. getLocalAddress ?
            this.buffers -= k
            debugPrintln(s"Read EOF; closed ${k}")
            c.close()
            return Seq()
        }

        debugPrintln(s"Read from ${k} ${buffer.position()} bytes...")
        buffer.flip()
        val bytes = new Array[Byte](buffer.remaining())  // !!! new -- cf. Array[](){}
        buffer.get(bytes);
        val msg = new String(bytes).trim
        debugPrintln(s"Read from ${k}: ${msg}")

        val stop = msg.indexOf(".")
        if (stop == -1) {
            Seq()
        } else {
            /*val fst = msg.slice(0, stop)
            if (stop == msg.length - 1) {
                //println(s"Actor(${name}) Buffer cleared: " + msg.length + " ,, " + stop)
                buffer.clear
            } else {
                //println(s"Actor(${name}) Buffer carrying over: " + (msg.length - stop+1) + " ,, " + stop)
                this.buffers(k) = ByteBuffer.wrap(msg.substring(stop+1).getBytes())  // !!! FIXME flip
            }
            Some(fst)*/
            val last = msg.lastIndexOf(".")
            val split = msg.split("\\.")
            if (last == msg.length - 1) {
                buffer.clear
                split
            } else {
                this.buffers(k) = ByteBuffer.wrap(msg.substring(last+1).getBytes())  // !!! FIXME flip
                split.slice(0, split.length - 1)
            }
        }
    }


    /* Debug */

    override def debugPrint(x: String): Unit = super.debugPrint(s"${debugToString(x)}")
    override def errPrint(x: String): Unit = super.errPrint(s"${debugToString(x)}")

    def debugToString(x: String): String = s"${nameToString()} ${x}"

    //def nameToString(): String = s"Actor(${name})"
    def nameToString(): String = this.name
}
