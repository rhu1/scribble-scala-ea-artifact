package ea.example.id.Id.Proto1

import ea.runtime.{AP, Session}

object Proto1 {
    val name: String = "Proto1"
    val roles: Seq[Session.Role] = Seq("S", "C")
}

class Proto1 extends AP(Proto1.name, Proto1.roles.toSet)