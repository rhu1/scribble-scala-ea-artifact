package ea.runtime

trait DebugPrinter {
    var debug = false
    def debugPrint(x: String): Unit = if (debug) { print(x) }
    def debugPrintln(x: String): Unit = debugPrint(s"${x}\n")
    def errPrint(x: String) = Console.err.print(s"[ERROR] ${x}")
    def errPrintln(x: String) = errPrint(s"${x}\n")
}

object Net {  // Used by EventDrivenServer
    type Host = String
    type Port = Int
    type Pid_C = String
    //type Liota = (Net.Pid, Int)
    type Liota = String
}

object Util {

    def spawn(f: () => Unit): Thread = {
        val t = new Thread {
            override def run(): Unit = f()
        }
        t.start()
        t
    }
}
