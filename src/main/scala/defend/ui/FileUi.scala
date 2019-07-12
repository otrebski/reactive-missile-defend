package defend.ui

import java.io.FileOutputStream

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster.{ Cluster, ClusterEvent }
import akka.event.Logging.MDC
import defend.cluster.Roles
import defend.model._

import scala.collection.immutable
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

/**
 * FileUI actor is saving game status to file, so it can be easily parsed with CLI and displayed on
 * Raspberry PI devices like PiFace
 * @param file output file
 */
class FileUi(val file: String) extends Actor with DiagnosticActorLogging {

  private var commandCenters: Map[String, CommandCenter] = Map.empty[String, CommandCenter]
  implicit val ec = scala.concurrent.ExecutionContext.global

  override def mdc(currentMessage: Any): MDC = {
    Map("node" -> Cluster(context.system).selfAddress.toString)
  }

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(1 second, self, StatusKeeper.Protocol.UpdateRequest)
    Cluster(context.system).subscribe(self, ClusterEvent.initialStateAsEvents,
                                      classOf[MemberUp],
                                      classOf[MemberRemoved],
                                      classOf[UnreachableMember],
                                      classOf[ReachableMember])
  }

  override def receive: Receive = {
    case m: MemberUp if m.member.hasRole(Roles.Tower) =>
      val address: String = m.member.address.toString
      val cc: CommandCenter = commandCenters.getOrElse(address, CommandCenter(name                 = address, status = CommandCenterOnline, lastMessageTimestamp = 0))
      commandCenters = commandCenters.updated(address, cc.copy(status = CommandCenterOnline))
      update()
    case m: MemberRemoved if m.member.hasRole(Roles.Tower) =>
      val address: String = m.member.address.toString
      val cc: CommandCenter = commandCenters.getOrElse(address, CommandCenter(name                 = address, status = CommandCenterOffline, lastMessageTimestamp = 0))
      commandCenters = commandCenters.updated(address, cc.copy(status = CommandCenterOffline))
      update()
    case m: UnreachableMember if m.member.hasRole(Roles.Tower) =>
      val address: String = m.member.address.toString
      val cc: CommandCenter = commandCenters.getOrElse(address, CommandCenter(name                 = address, status = CommandCenterUnreachable, lastMessageTimestamp = 0))
      commandCenters = commandCenters.updated(address, cc.copy(status = CommandCenterUnreachable))
      update()
    case m: ReachableMember if m.member.hasRole(Roles.Tower) =>
      val address: String = m.member.address.toString
      val cc: CommandCenter = commandCenters.getOrElse(address, CommandCenter(name                 = address, status = CommandCenterOnline, lastMessageTimestamp = 0))
      commandCenters = commandCenters.updated(address, cc.copy(status = CommandCenterOnline))
      update()
  }

  def update(): Unit = {
    val statusString: String = FileUi.warTheaterToString(commandCenters)
    val result: Try[Unit] = Try {
      val stream: FileOutputStream = new FileOutputStream(file)
      stream.write(statusString.getBytes("UTF-8"))
      stream.close()
    }
    if (result.isFailure) {
      log.error(result.failed.get, s"Can't save result to file $file")
    }
  }

}

object FileUi {
  private val DefaultStatus = Map(CommandCenterOnline -> 0, CommandCenterOffline -> 0, CommandCenterUnreachable -> 0)

  def props(file: String) = Props(classOf[FileUi], file)

  def warTheaterToString(commandCenters: Map[String, CommandCenter]): String = {
    val statusCount: Map[CommandCenterStatus, Iterable[CommandCenter]] = commandCenters.values.groupBy(_.status)
    val map: immutable.Iterable[String] = statusCount.map(v => s"${v._1}:${v._2.size}")
    //    DefaultStatus.compose(map)
    //TODO merge default with map
    map.mkString("\n")
  }
}