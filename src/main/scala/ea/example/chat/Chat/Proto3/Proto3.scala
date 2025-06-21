package ea.example.chat.Chat.Proto3

import ea.runtime.{AP, Session}

object Proto3 {
    val name: String = "Proto3"
    val roles: Seq[Session.Role] = Seq("R3", "C3")
}

class Proto3 extends AP(Proto3.name, Proto3.roles.toSet)