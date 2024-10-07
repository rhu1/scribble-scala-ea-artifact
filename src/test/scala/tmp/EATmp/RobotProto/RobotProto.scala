package tmp.EATmp.RobotProto

import ea.runtime.{AP, Session}

object RobotProto {
	val name: String = "RobotProto"
	val roles: Seq[Session.Role] = Seq("R", "D", "W")
}

class RobotProto extends AP(RobotProto.name, RobotProto.roles.toSet)
