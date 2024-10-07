package tmp.EATmp.ChatProto3

import ea.runtime.{AP, Session}

object ChatProto3 {
	val name: String = "ChatProto3"
	val roles: Seq[Session.Role] = Seq("R", "C")
}

class ChatProto3 extends AP(ChatProto3.name, ChatProto3.roles.toSet)