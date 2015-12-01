package play


import java.util.concurrent.ExecutorService

import scalaz.
  { \/, Applicative, EitherT, Functor, Kleisli, Monad, Monoid,
    Nondeterminism, Traverse }
import scalaz.concurrent.{ Strategy, Task }
import scalaz.std.anyVal._
import scalaz.std.list._
import scalaz.syntax.applicative._
import scalaz.syntax.either._
import scalaz.syntax.monoid._
import scalaz.syntax.traverse._


object Scratch extends App {

  case class Par[A](run: ExecutorService => Task[A]) extends AnyVal {

    def runDefault: Task[A] = run(Strategy.DefaultExecutorService)

    def map[B](f: A => B): Par[B] = Functor[Par].map(this)(f)

    def serial: Serial[A] = Serial(Kleisli(run))

    def modTask[B](f: Task[A] => Task[B]): Par[B] =
      Par { pool => f(run(pool)) }

  }

  object Par {

    def lift[A](task: Task[A]): Par[A] = Par(Function const task)

    implicit val applicative: Applicative[Par] =
      new Applicative[Par] {
        def point[A](a: => A) =
          Par lift a.point[Task]
        def ap[A, B](a: => Par[A])(f: => Par[A => B]): Par[B] =
          Par { pool =>
            Nondeterminism[Task].mapBoth(
              Task.fork(f run pool)(pool),
              Task.fork(a run pool)(pool)
            ) { _ apply _ }
          }
      }

    implicit val liftPar: LiftPar[Par] =
      new LiftPar[Par] {
        def lift[A](par: Par[A]) = par
      }

  }

  case class Serial[A]
      (kleisli: Kleisli[Task, ExecutorService, A]) extends AnyVal {

    def run(pool: ExecutorService): Task[A] = kleisli run pool

    def runDefault: Task[A] = run(Strategy.DefaultExecutorService)

    def par: Par[A] = Par(kleisli.run)

    def modTask[B](f: Task[A] => Task[B]): Serial[B] = Serial(kleisli.mapK(f))

  }

  object Serial {

    def lift[A](task: Task[A]): Serial[A] =
      Serial(Kleisli(Function const task))

    implicit val monad: Monad[Serial] =
      new Monad[Serial] {
        def point[A](a: => A) =
          Serial(a.point[Kleisli[Task, ExecutorService, ?]])
        def bind[A, B](a: Serial[A])(f: A => Serial[B]): Serial[B] =
          Serial(a.kleisli.flatMap { a => f(a).kleisli })
      }

    implicit val liftPar: LiftPar[Serial] =
      new LiftPar[Serial] {
        def lift[A](serial: Serial[A]) = serial.par
      }

  }

  trait LiftPar[F[_]] {
    def lift[A](fa: F[A]): Par[A]
  }

  object LiftPar {

    def apply[F[_]](implicit ev: LiftPar[F]): LiftPar[F] = ev

    implicit val taskLiftPar: LiftPar[Task] =
      new LiftPar[Task] {
        def lift[A](task: Task[A]) = Par lift task
      }

  }

  type Action[A, B] = EitherT[Task, A, B]

  object Action {

    def apply[A, B](b: => B): Action[A, B] = EitherT right (Task delay b)

    def fault[A, B](a: A): Action[A, B] = EitherT left (Task now a)

    def mapConcurrently[F[_] : Traverse, G[_]: LiftPar, A, B, C]
        (fa: F[EitherT[G, A, B]])
        (f: F[A \/ B] => A \/ C)
        : EitherT[Par, A, C] =
      EitherT(
        Functor[F]
          .map(fa) { et => LiftPar[G] lift et.run }
          .sequence[Par, A \/ B]
          .map(f))

    def foldConcurrently[F[_] : Traverse, G[_]: LiftPar, A, B : Monoid]
        (fa: F[EitherT[G, A, B]]): EitherT[Par, A, B] =
      mapConcurrently(fa) { bs =>
        bs.foldLeft(mzero[B].right[A]) { (x, y) => (x |@| y) { _ |+| _ } }
      }

  }

  val range = 1 to 10
  type Fault = String

  def delayedInt(value: Int, delay: Int): Action[Fault, Int] =
    if (value == 3)
      Action.fault(s"no ${value} for you")
    else
      Action {
        println(s"START: ${value}, TIMESTAMP: ${stopwatch}ms")
        Thread.sleep(delay)
        println(s"STOP: ${value}, TIMSTAMP: ${stopwatch}ms")
        value
      }

  val startTime: Long = System.nanoTime

  def stopwatch: Long =
    (System.nanoTime - startTime) / 1000000

  val actions =
    range
      .map { n => delayedInt(n, 5000) }
      .toList

  println(
    Action
      .foldConcurrently(actions)
      .run
      .runDefault
      .run)

}
