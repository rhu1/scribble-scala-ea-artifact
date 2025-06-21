package ea.example.savina.pingself.PingSelf.ProtoC

import ea.runtime.{AP, Session}

object ProtoC {
    val name: String = "ProtoC"
    val roles: Seq[Session.Role] = Seq("C", "PingDecisionMaker", "PingDecisionReceiver")
}

class ProtoC extends AP(ProtoC.name, ProtoC.roles.toSet)