package tmp.EATmp.PingPong2

import ea.runtime.{AP, Session}

object PingPong2 {
    val name: String = "PingPong2"
    val roles: Seq[Session.Role] = Seq("Pinger", "Ponger")
}

class PingPong2 extends AP(PingPong2.name, PingPong2.roles.toSet)