package tmp.EATmp.Phil1

import ea.runtime.{AP, Session}

object Phil1 {
    val name: String = "Phil1"
    val roles: Seq[Session.Role] = Seq("M", "P1")
}

class Phil1 extends AP(Phil1.name, Phil1.roles.toSet)