package defend.cluster

import akka.actor.{ ActorSystem, PoisonPill }
import akka.cluster.singleton.{ ClusterSingletonManager, ClusterSingletonManagerSettings }
import defend.ui.StatusKeeper
import pl.project13.scala.rainbow._

trait StatusKeeperSingleton {

  println("Creating status keeper".green)
  val system: ActorSystem
  val props = ClusterSingletonManager.props(
    singletonProps     = StatusKeeper.props(),
    terminationMessage = PoisonPill,
    settings           = ClusterSingletonManagerSettings(system).withSingletonName("statusKeeper"))
  val statusKeeperSingleton = system.actorOf(props, name = "singleton")
}
