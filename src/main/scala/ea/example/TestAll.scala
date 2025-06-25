package ea.example

import ea.example.chat.TestChatServer
import ea.example.id.TestId
import ea.example.lockid.TestLockId
import ea.example.robot.TestRobot
import ea.example.savina.diningself.TestDiningSelf
import ea.example.savina.dining.TestDining
import ea.example.savina.fib.TestFib
import ea.example.savina.ping.TestPing
import ea.example.savina.pingself.TestPingSelf
import ea.example.savina.sieve.TestSieve
import ea.example.shoprestock.TestShop
import ea.runtime.Util

object TestAll {
    def main(args: Array[String]): Unit = {
        //print("Hello")

        println("\n---\nId")
        Util.spawn(() => TestId.main(Array()))
        Thread.sleep(5000)
        id.S.enqueueClose()
        Thread.sleep(3000)  // Id has 2000ms sleep

        println("\n---\nLockId")
        Util.spawn(() => TestLockId.main(Array()))
        Thread.sleep(5000)
        lockid.S.enqueueClose()
        Thread.sleep(3000)  // LockId has 2000ms sleep

        println("\n---\nShop")
        Util.spawn(() => TestShop.main(Array()))
        Thread.sleep(5000)
        shoprestock.C.enqueueClose()
        shoprestock.P.enqueueClose()
        shoprestock.SF.enqueueClose()
        shoprestock.S.enqueueClose()
        Thread.sleep(1000)

        println("\n---\nRobot")
        Util.spawn(() => TestRobot.main(Array()))
        Thread.sleep(5000)
        robot.W.enqueueClose()  // Internally closes D, Rs
        Thread.sleep(1000)

        println("\n---\nChat")
        Util.spawn(() => TestChatServer.main(Array()))
        Thread.sleep(5000)
        chat.ChatServer.enqueueClose()  // Internally closes clients
        Thread.sleep(1000)

        println("\n---\nPingSelf")
        TestPingSelf.main(Array())
        Thread.sleep(1000)

        println("\n---\nPing")
        TestPing.main(Array())
        Thread.sleep(1000)

        println("\n---\nFib")
        TestFib.main(Array())
        Thread.sleep(1000)

        println("\n---\nDining-self")
        TestDiningSelf.main(Array())
        Thread.sleep(1000)

        println("\n---\nDining")
        TestDining.main(Array())
        Thread.sleep(1000)

        println("\n---\nSieve")
        TestSieve.main(Array())
    }
}
