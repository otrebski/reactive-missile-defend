package defend.cluster

import akka.actor.{ ActorRef, ActorSystem }
import akka.cluster.sharding.ClusterSharding
import defend.shard.TowerGuard

trait TowerShardProxy {

  val statusKeeperProxy: ActorRef
  val system: ActorSystem

  val towerShardProxy: ActorRef = {
    ClusterSharding(system).startProxy(
      typeName        = TowerGuard.shardRegion,
      role            = Some(Roles.Tower),
      extractEntityId = TowerGuard.extractEntityId,
      extractShardId  = TowerGuard.extractShardId
    )
  }

}
