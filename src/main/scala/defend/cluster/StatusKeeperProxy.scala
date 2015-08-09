package defend.cluster

import akka.actor.ActorSystem
import akka.contrib.pattern.ClusterSingletonProxy
import pl.project13.scala.rainbow.Rainbow._
trait StatusKeeperProxy {

  println("Creating status keeper proxy".green)
  val system: ActorSystem

  lazy val statusKeeperProxy = system.actorOf(
    ClusterSingletonProxy.props(
      singletonPath = "/user/singleton/statusKeeper",
      role          = None
    ),
    name = "statusKeeperProxy"
  )
}
