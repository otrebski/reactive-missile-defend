package defend.cluster

import akka.actor.{ ActorRef, ActorSystem }
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings }
import defend.shard.TowerGuard
import pl.project13.scala.rainbow._
trait TowerShard {

  val statusKeeperProxy: ActorRef
  val system: ActorSystem

  //  ClusterSharding.
  println("Starting tower shard  ".white.onBlue)
  val settings: ClusterShardingSettings = ClusterShardingSettings(system).withRole(Some(Roles.Tower))
  val towerShard: ActorRef = {
    ClusterSharding(system).start(
      typeName        = TowerGuard.shardRegion,
      entityProps     = TowerGuard.props(statusKeeperProxy),
      settings        = settings,
      extractEntityId = TowerGuard.extractEntityId,
      extractShardId  = TowerGuard.extractShardId)
  }
  println(s"Tower shard started: $towerShard ".white.onBlue)

}
