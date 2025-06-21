package ea.example.savina.fib.Fib.Proto2

import ea.runtime.{AP, Session}

object Proto2 {
    val name: String = "Proto2"
    val roles: Seq[Session.Role] = Seq("P", "C1", "C2")
}

class Proto2 extends AP(Proto2.name, Proto2.roles.toSet)