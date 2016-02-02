package defend.shard

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{ Actor, ActorRef, OneForOneStrategy, Props }
import akka.cluster.sharding.ShardRegion
import akka.pattern.PipeToSupport
import com.datastax.driver.core.exceptions.ReadFailureException
import defend.Envelope
import defend.shard.MessageLostTracker.LastMessageId
import defend.shard.TowerActor.Protocol.Situation

import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.language.postfixOps

class TowerGuard(statusKeeper: ActorRef, reloadTime: FiniteDuration) extends Actor with PipeToSupport {

  private var towerActor: ActorRef = _
  private var lostMessagesActor: Option[ActorRef] = None

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    val name: String = self.path.name
    towerActor = context.actorOf(TowerActor.props(name, statusKeeper, reloadTime))
  }

  override def receive: Receive = {
    case s: Situation =>
      towerActor forward s
      if (lostMessagesActor.isEmpty) {
        lostMessagesActor = Some(context.actorOf(MessageLostTracker.props(s.me, statusKeeper)))
      }
      lostMessagesActor.foreach(_ ! LastMessageId(s.index))

    case a: Any =>
      towerActor forward a
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _: ReadFailureException => Restart
      case _: Exception            => Restart
    }

}

object TowerGuard {
  def props(statusKeeper: ActorRef, reloadTime: FiniteDuration = 2 seconds): Props = Props(new TowerGuard(statusKeeper, reloadTime))

  val shardRegion = "tower"
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case Envelope(id, payload) ⇒ (id.toString, payload)
  }

  val numberOfShards = 100

  val extractShardId: ShardRegion.ExtractShardId = {
    case Envelope(id, _) ⇒ (id.hashCode % numberOfShards).toString
  }
}
