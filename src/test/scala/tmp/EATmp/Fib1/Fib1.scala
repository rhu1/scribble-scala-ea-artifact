package tmp.EATmp.Fib1

import ea.runtime.{AP, Session}

object Fib1 {
    val name: String = "Fib1"
    val roles: Seq[Session.Role] = Seq("P", "C")
}

class Fib1 extends AP(Fib1.name, Fib1.roles.toSet)