package tmp.EATmp.ChatProto2

import ea.runtime.{AP, Session}

object ChatProto2 {
	val name: String = "ChatProto2"
	val roles: Seq[Session.Role] = Seq("C", "R")
}

class ChatProto2 extends AP(ChatProto2.name, ChatProto2.roles.toSet)