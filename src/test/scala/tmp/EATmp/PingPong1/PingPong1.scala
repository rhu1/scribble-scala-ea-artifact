package tmp.EATmp.PingPong1

import ea.runtime.{AP, Session}

object PingPong1 {
    val name: String = "PingPong1"
    val roles: Seq[Session.Role] = Seq("C", "Pinger", "Ponger", "PongReceiver")
}

class PingPong1 extends AP(PingPong1.name, PingPong1.roles.toSet)