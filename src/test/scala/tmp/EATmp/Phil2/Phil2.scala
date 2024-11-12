package tmp.EATmp.Phil2

import ea.runtime.{AP, Session}

object Phil2 {
    val name: String = "Phil2"
    val roles: Seq[Session.Role] = Seq("P", "A")
}

class Phil2 extends AP(Phil2.name, Phil2.roles.toSet)