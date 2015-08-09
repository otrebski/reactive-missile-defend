package defend.cluster

import akka.actor.{ ActorSystem, PoisonPill }
import akka.contrib.pattern.ClusterSingletonManager
import defend.ui.StatusKeeper

import pl.project13.scala.rainbow.Rainbow._
trait StatusKeeperSingleton {

  println("Creating status keeper".green)
  val system: ActorSystem
  val statusKeeperSingleton = system.actorOf(
    ClusterSingletonManager.props(
      singletonProps     = StatusKeeper.props(),
      singletonName      = "statusKeeper",
      terminationMessage = PoisonPill,
      role               = None
    ),
    name = "singleton"
  )
}
