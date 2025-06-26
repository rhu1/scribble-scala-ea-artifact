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

        testAsync("Id", () => TestId.main(Array()), 5000, () => {
            id.S.enqueueClose()
            Thread.sleep(3000) // Id has 2000ms sleep
        })

        testAsync("LockId", () => TestLockId.main(Array()), 5000, () => {
            lockid.S.enqueueClose()
            Thread.sleep(3000) // LockId has 2000ms sleep
        })

        testAsync("Shop", () => TestShop.main(Array()), 5000, () => {
            shoprestock.C.enqueueClose()
            shoprestock.P.enqueueClose()
            shoprestock.SF.enqueueClose()
            shoprestock.S.enqueueClose()
            Thread.sleep(1000)
        })

        testAsync("Robot", () => TestRobot.main(Array()), 5000, () => {
            robot.W.enqueueClose() // Internally closes D, Rs
            Thread.sleep(1000)
        })

        testAsync("Chat", () => TestChatServer.main(Array()), 5000, () => {
            chat.ChatServer.enqueueClose() // Internally closes clients
            Thread.sleep(1000)
        })

        test("Ping-self", () => TestPingSelf.main(Array()))
        test("Ping", () => TestPing.main(Array()))
        test("Fib", () => TestFib.main(Array()))
        test("Dining-self", () => TestDiningSelf.main(Array()))
        test("Dining", () => TestDining.main(Array()))
        test("Sieve", () => TestSieve.main(Array()))

    }

    def testAsync(name: String, f: () => Unit, timeout: Int, close: () => Unit): Unit =
        val tmp = "-" * name.length
        println(s"\n$tmp\n$name\n$tmp\n")
        Util.spawn(f)
        Thread.sleep(timeout)
        close()

    def test(name: String, f: () => Unit): Unit =
        val tmp = "-" * name.length
        println(s"\n$tmp\n$name\n$tmp\n")
        f()
}
