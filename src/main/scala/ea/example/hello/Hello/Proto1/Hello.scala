package ea.example.hello.Hello.Proto1

import ea.example.hello.Hello
import ea.runtime.{AP, Session}

object Hello {
    val name: String = "Hello"
    val roles: Seq[Session.Role] = Seq("A", "B")
}

class Hello extends AP(Hello.name, Hello.roles.toSet)