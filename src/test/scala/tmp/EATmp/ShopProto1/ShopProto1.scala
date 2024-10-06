package tmp.EATmp.ShopProto1

import ea.runtime.{AP, Session}

object Shop {
	val name: String = "Shop"
	val roles: Seq[Session.Role] = Seq("C", "S", "P")
}

class Shop extends AP(Shop.name, Shop.roles.toSet)