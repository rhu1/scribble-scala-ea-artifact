package ea.example.hello

import ea.runtime.{AP, Session}

object Hello {
    val name: String = "Hello"
    val roles: Seq[Session.Role] = Seq("A", "B")
}

class Hello extends AP(Hello.name, Hello.roles.toSet)