package defend.shard

import akka.actor.{ Props, ActorRef }
import akka.persistence._
import defend.model.DefenceTower
import defend.shard.MessageLostTracker.LastMessageId
import defend.ui.StatusKeeper
import pl.project13.scala.rainbow.Rainbow._
class MessageLostTracker(tower: DefenceTower, statusKeeper: ActorRef) extends PersistentActor {

  def persistenceId: String = s"messageLostTracker-${tower.name}"

  private var lastMessageId = 0

  override def receiveRecover: Receive = {
    case LastMessageId(id) => lastMessageId = id
    case SnapshotOffer(criteria: SnapshotMetadata, LastMessageId(id)) =>
      lastMessageId = id
    case RecoveryCompleted =>
      println(s"Recover completed for $persistenceId".green)
  }

  override def receiveCommand: Receive = {
    case m @ LastMessageId(id) =>
      persist(m) {
        x =>
          if (id - lastMessageId > 1) {
            statusKeeper ! StatusKeeper.Protocol.LostMessages(tower, id - lastMessageId, System.currentTimeMillis())
          }
          lastMessageId = id
      }
      if (id % 30 == 0) {
        saveSnapshot(m)
      }
    case SaveSnapshotSuccess(metadata) =>
      println(s"Snapshot for $persistenceId saved: $metadata".cyan)
      deleteMessages(toSequenceNr = metadata.sequenceNr)

    case SaveSnapshotFailure(metadata, cause) =>
      println(s"Snapshot for $persistenceId not saved: $cause".red)

    case m: DeleteMessagesSuccess =>
      println(s"Successfully delete messages $m for $persistenceId".cyan)
  }
}

object MessageLostTracker {
  def props(tower: DefenceTower, statusKeeper: ActorRef): Props = {
    Props(new MessageLostTracker(tower, statusKeeper))
  }

  case class LastMessageId(id: Int)
}