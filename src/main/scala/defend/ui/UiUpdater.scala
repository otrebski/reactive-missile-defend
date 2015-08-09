package defend.ui

import javax.swing.SwingUtilities

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import defend.model.WarTheater

import scala.concurrent.duration._

class UiUpdater(jWarTheater: JWarTheater, statusKeeper: ActorRef) extends Actor with ActorLogging {

  implicit val ec = scala.concurrent.ExecutionContext.global

  var lastReceivedWarTheaterTimestamp = 0l
  val offline = false

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    context.system.scheduler.schedule(1 second, 25 millis, statusKeeper, StatusKeeper.Protocol.UpdateRequest)
    context.system.scheduler.schedule(2 second, 250 millis, self, UiUpdater.Ping)
  }

  override def receive: Receive = {
    case w: WarTheater =>
      //TODO time provider
      lastReceivedWarTheaterTimestamp = System.currentTimeMillis()
      log.debug("Received war theater")
      if (System.currentTimeMillis() - w.timestamp < 1000) {
        SwingUtilities.invokeAndWait(new Runnable {
          override def run(): Unit = {
            log.debug("Updating UI")
            jWarTheater.updateState(w)
          }
        })
      } else {
        log.info("skipping gui update because model is too old")
      }
    case UiUpdater.Ping =>
      if (lastReceivedWarTheaterTimestamp < System.currentTimeMillis() - 1000) {
        updateOffline(Some(lastReceivedWarTheaterTimestamp))
      } else if (!offline) {
        updateOffline(None)
      }

  }

  def updateOffline(offlineSince: Option[Long]) = {
    SwingUtilities.invokeLater {
      new Runnable {
        override def run(): Unit = {
          jWarTheater.offlineSince = offlineSince
          jWarTheater.repaint()
        }
      }
    }

  }
}

object UiUpdater {
  case object Ping
  def props(jWarTheater: JWarTheater, statusKeeper: ActorRef): Props = Props(classOf[UiUpdater], jWarTheater, statusKeeper)
}