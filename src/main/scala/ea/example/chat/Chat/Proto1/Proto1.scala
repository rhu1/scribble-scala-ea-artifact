package ea.example.chat.Chat.Proto1

import ea.runtime.{AP, Session}

object Proto1 {
    val name: String = "Proto1"
    val roles: Seq[Session.Role] = Seq("C", "S")
}

class Proto1 extends AP(Proto1.name, Proto1.roles.toSet)