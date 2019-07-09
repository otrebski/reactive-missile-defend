package defend.shard

import akka.actor.{ ActorLogging, ActorRef, Props }
import akka.persistence._
import defend.model.DefenceTower
import defend.shard.MessageLostTracker.LastMessageId
import defend.ui.StatusKeeper
import pl.project13.scala.rainbow._

class MessageLostTracker(tower: DefenceTower, statusKeeper: ActorRef) extends PersistentActor with ActorLogging {

  def persistenceId: String = s"messageLostTracker-${tower.name}"

  private var lastMessageId = 0
  val snapshotEvery: Int = 30
  private val created = System.currentTimeMillis()

  override def receiveRecover: Receive = {
    case LastMessageId(id) => lastMessageId = id
    case SnapshotOffer(criteria: SnapshotMetadata, LastMessageId(id)) =>
      lastMessageId = id
    case RecoveryCompleted =>
      println(s"Recover completed for $persistenceId in ${System.currentTimeMillis() - created}ms".green)
      log.info("Recover completed for {} in {}ms", persistenceId, System.currentTimeMillis() - created)
  }

  override protected def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
    super.onRecoveryFailure(cause, event)
    println(s"Recovery for $persistenceId failed: ${cause.getMessage}".red)
    log.error(cause, "Recovery failed!")
  }

  override def receiveCommand: Receive = {
    case m @ LastMessageId(id) =>
      log.debug("received LastMessage({})", id)
      persist(m) {
        x =>
          if (id - lastMessageId > 1) {
            statusKeeper ! StatusKeeper.Protocol.LostMessages(tower, id - lastMessageId, System.currentTimeMillis())
          }
          lastMessageId = id
      }
      if (lastMessageId % snapshotEvery == 0) {
        log.debug("Saving snapshot")
        saveSnapshot(m)
      }
    case SaveSnapshotSuccess(metadata) =>
      log.info("Snapshot save successfully")
      deleteMessages(metadata.sequenceNr - 1)

    case SaveSnapshotFailure(metadata, cause) =>
      log.error(cause, s"Snapshot not saved: ${cause.getMessage}")
      println(s"Snapshot for $persistenceId not saved: $cause".red)
  }
}

object MessageLostTracker {
  def props(tower: DefenceTower, statusKeeper: ActorRef): Props = {
    Props(new MessageLostTracker(tower, statusKeeper))
  }

  case class LastMessageId(id: Int)

}
