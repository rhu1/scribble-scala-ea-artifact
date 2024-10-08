package tmp.EATmp.Proto01

import ea.runtime.{AP, Session}

object Proto01 {
    val name: String = "Proto01"
    val roles: Seq[Session.Role] = Seq("A", "B")
}

class Proto01 extends AP(Proto01.name, Proto01.roles.toSet)