package tmp.EATmp.PingPong2C

import ea.runtime.{AP, Session}

object PingPong2C {
    val name: String = "PingPong2C"
    val roles: Seq[Session.Role] = Seq("C", "PingDecisionMaker", "PingDecisionReceiver")
}

class PingPong2C extends AP(PingPong2C.name, PingPong2C.roles.toSet)