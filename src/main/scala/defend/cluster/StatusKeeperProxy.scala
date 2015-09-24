package defend.cluster

import akka.actor.ActorSystem
import akka.cluster.singleton.{ ClusterSingletonProxy, ClusterSingletonProxySettings }
import pl.project13.scala.rainbow.Rainbow._

trait StatusKeeperProxy {

  println("Creating status keeper proxy".green)
  val system: ActorSystem
  private lazy val settings: ClusterSingletonProxySettings =
    ClusterSingletonProxySettings(system)
      .withSingletonName("statusKeeper")

  lazy val statusKeeperProxy = system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/singleton",
      settings             = settings
    ),
    name = "statusKeeperProxy"
  )
}
