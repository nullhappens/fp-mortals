import scala.concurrent.Future

trait TerminalSync {
  def read(): String
  def write(t: String): Unit
}

trait TerminalAsync {
  def read(): Future[String]
  def write(t: String): Future[Unit]
}

trait Terminal[C[_]] {
  def read(): C[String]
  def write(t: String): C[Unit]
}

trait Execution[C[_]] {
  def chain[A, B](c: C[A])(f: A => C[B]): C[B]
  def create[B](b: B): C[B]
}

// defining echo function without language features
// def echo[C[_]](t: Terminal[C], e: Execution[C]): C[String] =
//   e.chain(t.read) { in: String =>
//     e.chain(t.write(in)) { _: Unit => 
//       e.create(in) 
//     }
//   }

// Same signature as Monad
object Execution {
  implicit class Ops[A, C[_]](c: C[A]) {
    def flatMap[B](f: A => C[B])(implicit e: Execution[C]): C[B] = e.chain(c)(f)
    def map[B](f: A => B)(implicit e: Execution[C]): C[B] = e.chain(c)(f andThen e.create)
  }
}

// With implicits then echo is easier to read
// def echo[C[_]](implicit t: Terminal[C], e: Execution[C]): C[String] = 
//   t.read.flatMap { in: String =>
//     t.write(in).map { _: Unit =>
//       in
//     }
//   }

// and flatMap/map allows us to use for comprehensions 
// def echo[C[_]](implicit t: Terminal[C], e: Execution[C]): C[String] = 
//   for {
//     in <- t.read
//      _ <- t.write(in)
//   } yield in



