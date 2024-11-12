package tmp.EATmp.Fib2

import ea.runtime.{AP, Session}

object Fib2 {
    val name: String = "Fib2"
    val roles: Seq[Session.Role] = Seq("P", "C1", "C2")
}

class Fib2 extends AP(Fib2.name, Fib2.roles.toSet)