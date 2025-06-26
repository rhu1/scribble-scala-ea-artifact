package ea.runtime

import java.io.IOException

object Done

object Session {

    type Global = String
    type Role = String
    type Sid = (Global, Int)  // Maybe Net ?

    trait Data


    /* Protocol/session states */

    trait State {
        val sid: Sid
        val role: Role
    }

    // dynamically linear if check(Not)Used contract observed
    trait DynLin {
        var isUsed: Boolean = false

        def checkNotUsed(): Unit =
            if (this.isUsed) {
                throw new LinearityException("Linearity violation, already used.")
            }
            this.isUsed = true

        def checkUsed(): Unit =
            if (!this.isUsed) {
                throw new LinearityException("Linearity violation, not used.")
            }
    }

    trait LinState extends State with DynLin

    trait ActorState[A <: Actor] extends LinState {

        val actor: A

        override def checkNotUsed(): Unit = {
            if (this.isUsed) {
                throw new LinearityException(s"${actor.debugToString("Linearity violation, already used.")}")
            }
            this.isUsed = true
        }

        override def checkUsed(): Unit = {
            if (!this.isUsed) {
                throw new LinearityException(s"${actor.debugToString("Linearity violation, not used.")}")
                //actor.close()
            }
        }
    }

    trait IState extends State  // not LinState -- IState instances always carry a linear successor; n.b. SuspendState is linear
    trait OState[A <: Actor] extends ActorState[A]
    trait SuspendState[A <: Actor] extends ActorState[A]

    trait End[A <: Actor] extends ActorState[A] {
        def finish(): Done.type = Done
    }


    /* Become */

    def freeze[A <: Actor, S <: Session.OState[A]]
            (s: S, f: (Session.Sid, Session.Role, A) => S): (LinSome[S], Done.type) =
        s.isUsed = true
        (LinSome(f(s.sid, s.role, s.actor)), Done)  // main point: f copy is not done

    def ibecome[D, S <: ActorState[Actor]]
            (d: D, a: LinSome[S], f: (D, S) => Done.type): Done.type = {
        val hack = a.t
        try {
            val s = a.get // at most once get
            val done = f(d, s)
            s.checkUsed()
            done
        } catch {
            case e: (IOException | LinearityException) =>
                hack.actor.end(hack.sid, hack.role)
                hack.actor.debugPrintln(s"become ${hack.sid}(${hack.role}) swallowing...")
                new Exception(e).printStackTrace()
                hack.actor.handleException(e, None, Some(hack.sid))  // also pass the exception
                Done
            case e: Exception =>
                hack.actor.errPrintln(hack.actor.debugToString("Caught unexpected..."))
                new Exception(e).printStackTrace()
                hack.actor.errPrintln(hack.actor.debugToString("Force stopping..."))
                hack.actor.enqueueClose()
                Done
        } finally {
            hack.isUsed = true
        }
    }


    /* Runtime-linear Option */

    sealed trait LinOption[+T <: DynLin] extends DynLin {
        def get: T
    }

    case class LinNone() extends LinOption[Nothing] {
        def get: Nothing = throw new NoSuchElementException("None.get")
    }

    object LinNone {
        def apply(): LinNone =
            val n = new LinNone()
            n.isUsed = true
            n
    }
    case class LinSome[T <: DynLin](private[runtime] val t: T) extends LinOption[T] {
        def get: T =
            checkNotUsed()
            this.isUsed = true
            t
    }
}

class LinearityException(msg: String = null, cause: Throwable = null) extends RuntimeException
