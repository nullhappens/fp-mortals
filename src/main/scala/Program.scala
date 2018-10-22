import java.time.Instant

import scalaz._
import Scalaz._
import contextual.{Prefix, Verifier}

import scala.concurrent.duration._

final case class Epoch(millis: Long) extends AnyVal {
  def +(d: FiniteDuration): Epoch = Epoch(millis + d.toMillis)
  def -(e: Epoch): FiniteDuration = (millis - e.millis).millis
}

object EpochInterpolator extends Verifier[Epoch] {
  def check(s: String): Either[(Int, String), Epoch] =
    try Right(Epoch(Instant.parse(s).toEpochMilli))
    catch { case _: Exception => Left((0, "not in ISO-8601 format")) }
}

object EpochInterpolatorImplicits {
  implicit class EpochMillisStringContext(sc: StringContext) {
    val epoch = Prefix(EpochInterpolator, sc)
  }
}

final case class MachineNode(id: String)

// WorldView aggregates the return values of all the methods in the algebras
// and adds a pending field to track unfulfilled requests.
final case class WorldView(backlog: Int,
                           agents: Int,
                           managed: NonEmptyList[MachineNode],
                           alive: Map[MachineNode, Epoch],
                           pending: Map[MachineNode, Epoch],
                           time: Epoch)

trait Drone[F[_]] {
  def getBacklog: F[Int]
  def getAgents: F[Int]
}

trait Machines[F[_]] {
  def getTime: F[Epoch]
  def getManaged: F[NonEmptyList[MachineNode]]
  def getAlive: F[Map[MachineNode, Epoch]]
  def start(node: MachineNode): F[MachineNode]
  def stop(node: MachineNode): F[MachineNode]
}

trait DynAgents[F[_]] {
  def initial: F[WorldView]
  def update(old: WorldView): F[WorldView]
  def act(world: WorldView): F[WorldView]
}

final class DynAgentsModule[F[_]: Monad](D: Drone[F], M: Machines[F])
    extends DynAgents[F] {

  // Basic sequential implementation
//  override def initial: F[WorldView] = {
//    for {
//      db <- D.getBacklog
//      da <- D.getAgents
//      mm <- M.getManaged
//      ma <- M.getAlive
//      mt <- M.getTime
//    } yield WorldView(db, da, mm, ma, Map.empty, mt)
//  }

  // As opposed to flatMap for sequential operations, Scalaz uses Apply syntax for parallel operations:
  override def initial: F[WorldView] =
    ^^^^(D.getBacklog, D.getAgents, M.getManaged, M.getAlive, M.getTime) {
      case (db, da, mm, ma, mt) => WorldView(db, da, mm, ma, Map.empty, mt)
    }

  // If a node has changed state, we remove it from pending
  // and if a pending action is taking longer than 10 minutes to do anything
  // we assume that it failed and forget that we asked to do it.
  override def update(old: WorldView): F[WorldView] = {
    for {
      snap <- initial
      changed = symdiff(old.alive.keySet, snap.alive.keySet)
      pending = (old.pending -- changed).filterNot {
        case (_, started) => (snap.time - started) >= 10.minutes
      }
      update = snap.copy(pending = pending)
    } yield update
  }

  private def symdiff[T](a: Set[T], b: Set[T]): Set[T] =
    (a union b) -- (a intersect b)

  override def act(world: WorldView): F[WorldView] = world match {
    case NeedsAgent(node) =>
      for {
        _ <- M.start(node)
        update = world.copy(pending = Map(node -> world.time))
      } yield update
    case Stale(nodes) =>
//      nodes.foldLeftM(world) { (world, n) =>
//        for {
//          _ <- M.stop(n)
//          update = world.copy(pending = world.pending + (n -> world.time))
//        } yield update
//      }
      for {
        stopped <- nodes.traverse(M.stop)
        updates = stopped.map(_ -> world.time).toList.toMap
        update = world.copy(pending = world.pending ++ updates)
      } yield update
    case _ => world.pure[F]
  }

  // We need to add agents to the farm
  // if there is a backlog of work, we have no agents, we have no nodes alive, and there are no pending actions.
  // We return a candidate node that we would like to start:
  private object NeedsAgent {
    def unapply(world: WorldView): Option[MachineNode] = world match {
      case WorldView(backlog, 0, managed, alive, pending, _)
          if backlog > 0 && alive.isEmpty && pending.isEmpty =>
        Option(managed.head)
      case _ => None
    }
  }

  // If there is no backlog, we should stop all nodes that have become stale (they are not doing any work).
  // However, since Google charge per hour we only shut down machines in their 58th minute to get the most out of our money.
  // We return the non-empty list of nodes to stop.
  // As a financial safety net, all nodes should have a maximum lifetime of 5 hours.”
  private object Stale {
    def unapply(world: WorldView): Option[NonEmptyList[MachineNode]] =
      world match {
        case WorldView(backlog, _, _, alive, pending, time) if alive.nonEmpty =>
          (alive -- pending.keys)
            .collect {
              case (n, started)
                  if backlog == 0 && (time - started).toMinutes % 60 >= 58 =>
                n
              case (n, started) if (time - started) >= 5.hours => n
            }
            .toList
            .toNel
        case _ => None
      }
  }

}
