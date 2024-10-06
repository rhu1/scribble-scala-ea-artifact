package tmp.EATmp.ShopProto2

import ea.runtime.{AP, Session}

object ShopProto2 {
	val name: String = "ShopProto2"
	val roles: Seq[Session.Role] = Seq("SS", "SF")
}

class ShopProto2 extends AP(ShopProto2.name, ShopProto2.roles.toSet)