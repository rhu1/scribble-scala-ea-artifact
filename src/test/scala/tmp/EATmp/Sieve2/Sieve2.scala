package tmp.EATmp.Sieve2

import ea.runtime.{AP, Session}

object Sieve2 {
    val name: String = "Sieve2"
    val roles: Seq[Session.Role] = Seq("F", "Fnext")
}

class Sieve2 extends AP(Sieve2.name, Sieve2.roles.toSet)