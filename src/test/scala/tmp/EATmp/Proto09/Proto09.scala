package tmp.EATmp.Proto09

import ea.runtime.{AP, Session}

object Proto09 {
	val name: String = "Proto09"
	val roles: Seq[Session.Role] = Seq("A", "B")
}

class Proto09 extends AP(Proto09.name, Proto09.roles.toSet)