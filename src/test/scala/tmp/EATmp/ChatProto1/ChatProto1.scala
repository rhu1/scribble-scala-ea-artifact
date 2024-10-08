package tmp.EATmp.ChatProto1

import ea.runtime.{AP, Session}

object ChatProto1 {
    val name: String = "ChatProto1"
    val roles: Seq[Session.Role] = Seq("C", "S")
}

class ChatProto1 extends AP(ChatProto1.name, ChatProto1.roles.toSet)