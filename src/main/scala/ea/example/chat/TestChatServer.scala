package ea.example.chat

import ea.example.chat.Chat.Proto1
import ea.runtime.Net.Port

import java.util.concurrent.LinkedTransferQueue


object TestChatServer {

    val PORT_Proto1: Port = ChatServer.PORT_Proto1
    val PORT_C1: Port = 7777
    val PORT_C2:Port = 7779

    val shutdown: LinkedTransferQueue[String] = LinkedTransferQueue()

    def main(args: Array[String]): Unit = {
        val ap1 = new Proto1.Proto1
        //ap1.debug = true
        ap1.spawn(ChatServer.PORT_Proto1)

        //server.debug = true
        ChatServer.spawn()

        val c1 = new ChatClient("client1", PORT_C1)
        //c1.debug = true
        val d1 = c1.spawn()
        c1.run(d1, PORT_Proto1)

        val c2 = new ChatClient("client2", PORT_C2)
        //c2.debug = true
        val d2 = c2.spawn()
        c2.run(d2, PORT_Proto1)

        // Only waiting externally for Server close -- others implicitly closed after
        println(s"Closed ${shutdown.take()}.")  // ChatServer
        c1.enqueueClose()
        c2.enqueueClose()

        for i <- 1 to 2 do println(s"Closed ${shutdown.take()}.")  // C1, C2
        println(s"Closing ${ap1.nameToString}...")
        ap1.close()
    }
}

