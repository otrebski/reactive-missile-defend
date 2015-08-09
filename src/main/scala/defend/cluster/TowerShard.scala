package defend.cluster

import akka.actor.{ ActorRef, ActorSystem }
import akka.contrib.pattern.ClusterSharding
import defend.shard.TowerActor
trait TowerShard {

  val statusKeeperProxy: ActorRef
  val system: ActorSystem
  val towerShard: ActorRef = {
    ClusterSharding(system).start(
      TowerActor.shardRegion,
      Some(TowerActor.props(statusKeeperProxy)),
      TowerActor.idExtractor,
      TowerActor.shardResolver(shardCount = 40)
    )
  }

}
