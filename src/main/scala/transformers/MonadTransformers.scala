package transformers

import scalaz._
import Scalaz._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object MonadTransformers extends App {

  def getA: Future[Option[Int]] = Future.successful(Some(5))

  def getB: Future[Option[Int]] = Future.successful(Some(10))

  def getC: Future[Int] = Future.successful(3)

  def getD: Option[Int] = Some(22)

  def getE: Int = 10

  val result: OptionT[Future, Int] = for {
    a <- OptionT(getA)
    b <- OptionT(getB)
  } yield a + b

  println(result)
  println(result.run)

  val result2 = for {
    a <- OptionT(getA)
    b <- OptionT(getB)
    c <- getC.liftM[OptionT]
  } yield a + b + c

  println(result2)
  println(result2.run)
  println(Await.result(result2.run, Duration.Inf))

  val result3 = for {
    a <- OptionT(getA)
    b <- OptionT(getB)
    c <- getC.liftM[OptionT]
    d <- OptionT(getD.pure[Future])
  } yield a + b + c + d
  println(Await.result(result3.run, Duration.Inf))

  // helper functions
  def liftFutureOption[A](f: Future[Option[A]]): OptionT[Future, A] = OptionT(f)
  def liftFuture[A](f: Future[A]): OptionT[Future, A] = f.liftM[OptionT]
  def liftOption[A](o: Option[A]): OptionT[Future, A] = OptionT(o.pure[Future])
  def lift[A](a: A): OptionT[Future, A] = liftOption(Option(a))

  // \> operator applies function on the right to value on the left

  val result4 = for {
    a <- getA |> liftFutureOption
    b <- getB |> liftFutureOption
    c <- getC |> liftFuture
    d <- getD |> liftOption
    e <- getE |> lift
  } yield a + b + c + d + e
  println(Await.result(result4.run, Duration.Inf))

}
