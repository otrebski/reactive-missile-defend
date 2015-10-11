package defend.cluster

import akka.actor.{ ActorRef, ActorSystem }
import akka.cluster.sharding.ClusterSharding
import defend.shard.TowerActor

trait TowerShardProxy {

  val statusKeeperProxy: ActorRef
  val system: ActorSystem

  val towerShardProxy: ActorRef = {
    ClusterSharding(system).startProxy(
      typeName        = TowerActor.shardRegion,
      role            = Some(Roles.Tower),
      extractEntityId = TowerActor.extractEntityId,
      extractShardId  = TowerActor.extractShardId
    )
  }

}
