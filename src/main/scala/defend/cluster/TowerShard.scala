package defend.cluster

import akka.actor.{ ActorRef, ActorSystem }
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings }
import defend.shard.TowerActor
trait TowerShard {

  val statusKeeperProxy: ActorRef
  val system: ActorSystem

  val towerShard: ActorRef = {
    ClusterSharding(system).start(
      typeName        = TowerActor.shardRegion,
      entityProps     = TowerActor.props(statusKeeperProxy),
      settings        = ClusterShardingSettings(system),
      extractEntityId = TowerActor.extractEntityId,
      extractShardId  = TowerActor.extractShardId
    )
  }

}
