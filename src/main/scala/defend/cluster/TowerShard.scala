package defend.cluster

import akka.actor.{ ActorRef, ActorSystem }
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings }
import defend.shard.TowerActor
import pl.project13.scala.rainbow.Rainbow._
trait TowerShard {

  val statusKeeperProxy: ActorRef
  val system: ActorSystem

  //  ClusterSharding.
  println("Starting tower shard  ".white.onBlue)
  val settings: ClusterShardingSettings = ClusterShardingSettings(system).withRole(Some(Roles.Tower))
  val towerShard: ActorRef = {
    ClusterSharding(system).start(
      typeName        = TowerActor.shardRegion,
      entityProps     = TowerActor.props(statusKeeperProxy),
      settings        = settings,
      extractEntityId = TowerActor.extractEntityId,
      extractShardId  = TowerActor.extractShardId
    )
  }
  println(s"Tower shard started: $towerShard ".white.onBlue)

}
