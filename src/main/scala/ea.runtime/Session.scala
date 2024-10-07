package ea.runtime

object Done

object Session {

    type Global = String
    type Role = String
    type Sid = (Global, Int)  // Maybe Net ?

    class Bar {
        type OK
    }

    trait Data

    // dynamically linear if check(Not)Used contract observed
    trait DynLin {
        var isUsed: Boolean = false

        //def checkNotUsed()(implicit debuggable): Unit = {
        def checkNotUsed(): Unit = {
            if (this.isUsed) {
                throw new RuntimeException("Linearity violation, already used.")
            }
            this.isUsed = true
        }

        //def checkUsed()(implicit debuggable): Unit = {
        def checkUsed(): Unit = {
            if (!this.isUsed) {
                throw new RuntimeException("Linearity violation, not used.")
            }
        }
    }

    trait State {
        val sid: Sid
    }

    trait LinState extends State with DynLin

    // CHECKME A unnecessary?
    trait ActorState[A <: Actor] extends LinState {  // Maybe integrate into State

        //class Foo

        val actor: A

        override def checkNotUsed(): Unit = {
            if (this.isUsed) {
                throw new RuntimeException(s"${actor.debugToString("Linearity violation, already used.")}")
                //actor.close()
            }
            this.isUsed = true
        }

        override def checkUsed(): Unit = {
            if (!this.isUsed) {
                throw new RuntimeException(s"${actor.debugToString("Linearity violation, not used.")}")
                //actor.close()
            }
        }

        /*def weaken(): (Done.type) = {
            this.isUsed = true
            Done
        }*/
    }

    trait IState extends State  // !!! not LinState -- IState instances always carry a linear successor; n.b. SuspendState is linear
    trait OState[A <: Actor] extends ActorState[A]
    trait SuspendState[A <: Actor] extends ActorState[A]  // !!! Not really a state

    trait End[A <: Actor] extends ActorState[A] {
        def finish(): Done.type = { Done }
    }

    trait EndFoo[A <: Actor, T] extends ActorState[A] {
        def finishFoo(): Unit = {}
        def getT(): T
    }


    /* ... */

    /*
    def cache[D, A <: Actor, S <: Session.OState[A]]
            (s: S, f: (Session.Sid, A) => S, g: Some[S] => Unit): Done.type = {
        val (a, done) = weaken(s, f)
        g(Some(a))
        done
    }*/

    // CHECKME LinSome copy-able? (case class...) -- does private help?
    // TODO generate f copier inside API (e.g., copy constructor -- cf. case class)
    //def weaken[A <: Actor, S <: Session.OState[A]]
    def freeze[A <: Actor, S <: Session.OState[A]]
            (s: S, f: (Session.Sid, A) => S): (LinSome[S], Done.type) = {
        s.isUsed = true
        (LinSome(f(s.sid, s.actor)), Done)  // main point: f copy is not done
    }

    def become[D, S <: ActorState[Actor]]
    //def become[D, A <: Actor, S <: ActorState[A]]  // FIXME probably due to Actor hardcoded in places
            (d: D, a: LinSome[S], f: (D, S) => Done.type): Done.type = {
        val s = a.get  // at most once get
        val done = f(d, s)
        s.checkUsed()
        done
    }


    /* ... */

    // Can hold a DynLin -- so itself must be "linear" (ar at least affine) to prevent copying? (or unnecessary? pass by ref...)
    // ...get should be private: LinSome should be passed to API (become) that handles DynLin according to contract
    sealed trait LinOption[+T <: DynLin] extends DynLin {  // !!! variance
        def get: T
    }
    case class LinNone() extends LinOption[Nothing] {  // cf. actual None
        def get: Nothing = throw new NoSuchElementException("None.get")
    }
    object LinNone {
        def apply(): LinNone = {
            val n = new LinNone()
            n.isUsed = true
            n
        }
    }
    case class LinSome[T <: DynLin](private val t: T) extends LinOption[T] {  // CHECKME private?
        def get: T = {
            checkNotUsed()
            this.isUsed = true
            t
        }
    }

}

//class SessionException(msg: String = null, cause: Throwable = null) extends Exception

/*object SessionException {
    def defaultMessage(message: String, cause: Throwable) =
        if (message != null) message
        else if (cause != null) cause.toString()
        else null
}*/
