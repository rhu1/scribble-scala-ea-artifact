package ea.runtime

import java.io.IOException
import java.net.{InetSocketAddress, SocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.*
import scala.collection.mutable.ListBuffer


abstract class EventServer(val name: String) extends DebugPrinter {

    private var isSelecting = false  // controls main select loop
    private var fServerSocket: Option[ServerSocketChannel] = None
    private var fSelector: Option[Selector] = None

    private var port: Int = -1

    def spawn(port: Int): Unit =
        this.port = port
        init()
        Util.spawn(() => runSelectLoop())

    // Pre: !this.isSelecting, this.serverSocket == None, this.selector == None
    @throws[IOException]
    private[runtime] def init(): Unit =
        if (this.isSelecting) {  // Implies this.selector and this.serverSocket not None (via run)
            errPrintln("Already isSelecting, cannot init again")
            return
        }
        val selector = Selector.open
        val serverSocket = ServerSocketChannel.open
        serverSocket.bind(new InetSocketAddress ("localhost", port))
        serverSocket.configureBlocking(false)
        serverSocket.register(selector, SelectionKey.OP_ACCEPT)
        this.fSelector = Some(selector)
        this.fServerSocket = Some(serverSocket)
        debugPrintln(s"Server bound: $port")

    // Local address
    private val sockets: collection.mutable.Set[SocketAddress] = collection.mutable.Set()

    // ...only for registerForPeers from user clients (cf. events from event loop)
    private[runtime] def registerWithSelector(c: SocketChannel, k: Int): Unit =
        c.register(this.fSelector.get, k)
        this.sockets += c.getLocalAddress

    // TODO consider Data
    def afterClosed(): Unit = {}

    def handleException(cause: Throwable, addr: Option[SocketAddress], sid: Option[Session.Sid]): Unit =
        cause.printStackTrace()

    // Post: !this.isSelecting, this.serverSocket == None, this.selector == None
    @throws[IOException]
    //private[runtime]
    def enqueueClose(): Unit =
        enqueueForSelectLoop(() => {
            debugPrintln("Stopping...")
            this.isSelecting = false
            try {
                this.fSelector.foreach(_.close)
            } finally {
                try {
                    this.fServerSocket.foreach(_.close)
                } finally {
                    this.fSelector = None
                    this.fServerSocket = None
                    afterClosed()
                }
            }
        })

    private val runQueueLock = new Object()
    private val runQueue = new ListBuffer[() => Unit]()

    private[runtime] def enqueueForSelectLoop(f: () => Unit): Unit =
        this.runQueueLock.synchronized {
            this.runQueue += f
        }
        if (this.fSelector.isDefined) {
            this.fSelector.get.wakeup()
        }

    // Pre: !this.isSelecting, this.serverSocket == Some, this.selector == Some
    @throws[IOException]
    private[runtime] def runSelectLoop(): Unit = {
        if (this.isSelecting) {
            errPrintln("Already isSelecting")
            return
        } else if (this.fServerSocket.isEmpty) {
            errPrintln("ServerSocket not open")
            return
        } else if (this.fSelector.isEmpty) {
            errPrintln("No Selector")
            return
        }

        this.isSelecting = true
        val selector = this.fSelector.get
        while (this.isSelecting) {

            this.runQueueLock.synchronized {
                while (this.runQueue.nonEmpty) {
                    val next = this.runQueue.remove(0)
                    next.apply()
                }
            }

            if (!selector.isOpen) { // cf. maybe done a runQueue close above
                if (this.isSelecting) {
                    throw new RuntimeException("[ERROR]")
                }
            } else {
                debugPrintln("Selecting...")
                selector.select()

                debugPrintln(s"...selected: ${selector.selectedKeys.toString}")
                // key has Sid attachment if any -- TODO: not done yet
                val keys = selector.selectedKeys.iterator
                while (keys.hasNext) {

                    // cf. CancelledKey
                    val key = keys.next()
                    val c = key.channel()
                    val a = key.attachment()
                    keys.remove()
                    if (key.isValid) {
                        var addr: Option[SocketAddress] = None
                        try {
                            addr = c match {
                                case cc: SocketChannel => Some(cc.getRemoteAddress)
                                case _ => None
                            }
                            // Concurrent channel failure can invalidate key
                            if (key.isValid && key.isAcceptable) {
                                handleAcceptAndRegister(selector, key)
                            } else if (key.isValid && key.isReadable) {
                                handleReadAndRegister(selector, key)
                            }
                        } catch {
                            case e: (IOException | LinearityException) =>
                                key.cancel()
                                debugPrintln("Swallowing...")
                                if (this.debug) { new Exception(e).printStackTrace() }
                                val opt = a match {
                                    //case x: Session.Sid => Some(x)  // FIXME because attachment Java Object is a Scala tuple
                                    case _ => None
                                }
                                handleException(e, addr, opt)
                            case e: Exception =>
                                errPrintln(debugToString("Caught unexpected..."))
                                new Exception(e).printStackTrace()
                                errPrintln(debugToString("Force stopping..."))
                                enqueueClose()
                                return
                        }
                    }
                }
            }
        }
        debugPrintln("Stopped.")
    }

    @throws[IOException]
    private def handleAcceptAndRegister(selector: Selector, key: SelectionKey): Unit =
        val serverSocket = key.channel.asInstanceOf[ServerSocketChannel]
        val client = accept(serverSocket)
        client.register(selector, SelectionKey.OP_READ)
        debugPrintln(s"Registered accepted for READ: ${client.getRemoteAddress}")

    @throws[IOException]
    private def handleReadAndRegister(selector: Selector, key: SelectionKey): Unit =
        val client = key.channel.asInstanceOf[SocketChannel]
        val opt = read(client)
        if (opt.isEmpty) {
            client.close()
            debugPrintln("Not accepting client messages anymore...")
        } else {
            val ms = opt
            ms.foreach(x => handleReadAndRegister(client, selector, x))
        }

    @throws[IOException]
    private[runtime] def handleReadAndRegister(client: SocketChannel, selector: Selector, msg: String): Unit

    @throws[IOException]
    private[runtime] def connectAndRegister(host: Net.Host, port: Net.Port): Option[SocketChannel] =
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


    /* Channel I/O -- independent of event loop */

    @throws[IOException]
    private def connect(host: Net.Host, port: Net.Port): SocketChannel =
        debugPrintln(s"$name connecting... $port")
        val sSocket = SocketChannel.open(new InetSocketAddress(host, port))
        sSocket.configureBlocking(false)
        debugPrintln(s"Connected Actor: ${sSocket.getRemoteAddress}")
        sSocket

    @throws[IOException]
    private def accept(serverSocket: ServerSocketChannel): SocketChannel =
        val client = serverSocket.accept()
        client.configureBlocking(false)
        debugPrintln(s"Accepted: ${client.getRemoteAddress}")
        client

    @throws[IOException]
    private[runtime] def write(c: SocketChannel, pay: String): Unit =
        val addr = c.getRemoteAddress
        val msg = s"$pay."
        val buffer = ByteBuffer.wrap(msg.getBytes)
        debugPrintln(s"Writing ${buffer.remaining()} bytes to $addr...")
        c.write(buffer)
        debugPrintln(s"...written to $addr: $msg")

    private type RemoteAddressString = String
    private val buffers: collection.mutable.Map[RemoteAddressString, ByteBuffer] = collection.mutable.Map()

    @throws[IOException]
    private def read(c: SocketChannel): Seq[String] = {
        val k = c.getRemoteAddress.toString
        val buffer = this.buffers.getOrElseUpdate(k, ByteBuffer.allocate(2048))  // !!!
        val r = c.read(buffer)
        if (r == -1) {
            this.buffers -= k
            debugPrintln(s"Read EOF; closing $k...")
            c.close()
            return Seq()
        }

        debugPrintln(s"Read from $k ${buffer.position()} bytes...")
        buffer.flip()
        val bytes = new Array[Byte](buffer.remaining())
        buffer.get(bytes)
        val msg = new String(bytes).trim
        debugPrintln(s"Read from $k: $msg")

        val stop = msg.indexOf(".")
        if (stop == -1) {
            Seq()
        } else {
            val last = msg.lastIndexOf(".")
            val split = msg.split("\\.")
            if (last == msg.length - 1) {
                buffer.clear
                split
            } else {
                this.buffers(k) = ByteBuffer.wrap(msg.substring(last+1).getBytes())
                split.slice(0, split.length - 1)
            }
        }
    }


    /* Debug */

    override def debugPrint(x: String): Unit = super.debugPrint(s"${debugToString(x)}")
    override def errPrint(x: String): Unit = super.errPrint(s"${debugToString(x)}")

    def debugToString(x: String): String = s"$nameToString $x"

    def nameToString: String = s"${this.name}"

}
