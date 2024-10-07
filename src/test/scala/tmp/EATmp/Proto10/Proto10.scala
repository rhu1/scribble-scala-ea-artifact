package tmp.EATmp.Proto10

import ea.runtime.{AP, Session}

object Proto10 {
	val name: String = "Proto10"
	val roles: Seq[Session.Role] = Seq("A", "B")
}

class Proto10 extends AP(Proto10.name, Proto10.roles.toSet)