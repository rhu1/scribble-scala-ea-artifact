package tmp.EATmp.ShopProto1

import ea.runtime.{AP, Session}

object ShopProto1 {
	val name: String = "ShopProto1"
	val roles: Seq[Session.Role] = Seq("C", "S", "P")
}

class ShopProto1 extends AP(ShopProto1.name, ShopProto1.roles.toSet)