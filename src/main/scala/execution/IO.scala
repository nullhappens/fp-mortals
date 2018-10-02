final class IO[A](val interpret: () => A) {
  def map[B](f: A => B): IO[B] = IO(f(interpret()))

  def flatMap[B](f: A => IO[B]): IO[B] = IO(f(interpret()).interpret())
}

object IO {
  def apply[A](a: => A): IO[A] = new IO(() => a)
}
