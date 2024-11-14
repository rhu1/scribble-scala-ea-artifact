package tmp.EATmp.Sieve1

import ea.runtime.{AP, Session}

object Sieve1 {
    val name: String = "Sieve1"
    val roles: Seq[Session.Role] = Seq("M", "G", "F1")
}

class Sieve1 extends AP(Sieve1.name, Sieve1.roles.toSet)