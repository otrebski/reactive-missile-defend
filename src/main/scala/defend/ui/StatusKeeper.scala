package defend.ui

import akka.actor._
import akka.cluster.ClusterEvent.{ MemberRemoved, MemberUp, ReachableMember, UnreachableMember }
import akka.cluster.{ Cluster, ClusterEvent }
import akka.event.Logging.MDC
import defend.PersistenceMonitor.{ PersistenceUnknown, PersistenceState }
import defend.cluster.Roles
import defend.model._
import defend.ui.StatusKeeper.Protocol._

import scala.language.postfixOps

class StatusKeeper(timeProvider: () => Long) extends Actor with DiagnosticActorLogging {

  private val explosionsDuration: Int = 1000
  private val KeepAliveTimeout = 500

  private var humanMissiles: List[WeaponInAction[HumanWeapon]] = Nil
  private var alienMissiles: List[WeaponInAction[AlienWeapon]] = Nil
  private var currentExplosions: List[Explosion] = Nil
  private var previousExplosions: List[(Explosion, Long)] = Nil
  private var cities: List[City] = Nil
  private var defenceTowers: List[DefenceTower] = Nil
  private var defenceTowersStatus: Map[String, DefenceTowerStatus] = Map.empty[String, DefenceTowerStatus]
  private var towersLastKeepAlive: Map[String, Long] = Map.empty[String, Long]
  private var commandCenters: Map[String, CommandCenter] = Map.empty[String, CommandCenter]
  private var points: Integer = 0
  private var landscape: Option[LandScape] = None
  private var persistenceState: PersistenceState = PersistenceUnknown

  override def mdc(currentMessage: Any): MDC = {
    Map("node" -> Cluster(context.system).selfAddress.toString)
  }

  override def receive: Receive = {
    case UpdateRequest =>
      log.debug("Received update request")
      sendStatus()
    case m: DefenceTowerStatus =>
      defenceTowersStatus = defenceTowersStatus.updated(m.defenceTower.name, m.copy(lastMessageTimestamp = Some(timeProvider())))
      towersLastKeepAlive = towersLastKeepAlive.updated(m.defenceTower.name, timeProvider())
      log.debug(s"Defence tower statuses after update $defenceTowersStatus")
    case m: OptionalWarTheater =>
      log.debug("Received optional war theater")
      if (m.landScape.isDefined) {
        landscape = m.landScape
      }
      humanMissiles = m.humanWeapons.getOrElse(humanMissiles)

      alienMissiles = m.alienWeapons.getOrElse(alienMissiles)

      previousExplosions = previousExplosions ::: currentExplosions.map {
        (_, timeProvider())
      }
      currentExplosions = m.explosions.getOrElse(currentExplosions)

      cities = m.cities.getOrElse(cities)

      defenceTowers = m.defence.getOrElse(defenceTowers)

      points = m.points.getOrElse(points)

    case m: TowerKeepAlive =>
      log.info(s"Received keepAlive from ${m.id}: $m")
      towersLastKeepAlive = towersLastKeepAlive.updated(m.id, timeProvider())
      defenceTowersStatus.get(m.id)
        .orElse(Some(DefenceTowerStatus(DefenceTower(m.id, Position(0, 0)), m.towerState, isUp = true, Some(m.commandCenterName), m.level)))
        .map {
          _.copy(commandCenterName = Some(m.commandCenterName), level = m.level, lastMessageTimestamp = Some(timeProvider()))
        }.foreach { x =>
          defenceTowersStatus = defenceTowersStatus.updated(m.id, x)
        }
      //update last message from defence tower
      commandCenters.get(m.commandCenterName).foreach(cc => commandCenters = commandCenters.updated(m.commandCenterName, cc.copy(lastMessageTimestamp = timeProvider())))
    case m: MemberUp if m.member.hasRole(Roles.Tower) =>
      val address: String = m.member.address.toString
      val cc: CommandCenter = commandCenters.getOrElse(address, CommandCenter(name = address, status = CommandCenterOnline, lastMessageTimestamp = 0))
      commandCenters = commandCenters.updated(address, cc.copy(status = CommandCenterOnline))
    case m: MemberRemoved if m.member.hasRole(Roles.Tower) =>
      val address: String = m.member.address.toString
      val cc: CommandCenter = commandCenters.getOrElse(address, CommandCenter(name = address, status = CommandCenterOffline, lastMessageTimestamp = 0))
      commandCenters = commandCenters.updated(address, cc.copy(status = CommandCenterOffline))
    case m: UnreachableMember if m.member.hasRole(Roles.Tower) =>
      val address: String = m.member.address.toString
      val cc: CommandCenter = commandCenters.getOrElse(address, CommandCenter(name = address, status = CommandCenterUnreachable, lastMessageTimestamp = 0))
      commandCenters = commandCenters.updated(address, cc.copy(status = CommandCenterUnreachable))

    case m: ReachableMember if m.member.hasRole(Roles.Tower) =>
      val address: String = m.member.address.toString
      val cc: CommandCenter = commandCenters.getOrElse(address, CommandCenter(name = address, status = CommandCenterOnline, lastMessageTimestamp = 0))
      commandCenters = commandCenters.updated(address, cc.copy(status = CommandCenterOnline))

    case p: PersistenceState => persistenceState = p
  }

  def sendStatus(): Unit = {
    previousExplosions = previousExplosions.filter(explosionFilter)
    val towerUp: List[DefenceTowerStatus] = defenceTowers.map {
      d =>
        val t: Long = towersLastKeepAlive.getOrElse(d.name, 0)
        val lastKeepAlive: Long = timeProvider() - t
        val up = lastKeepAlive < KeepAliveTimeout
        val n: Option[String] = defenceTowersStatus.get(d.name).flatMap(_.commandCenterName)
        DefenceTowerStatus(
          d,
          defenceTowerState    = defenceTowersStatus.get(d.name).map(s => s.defenceTowerState).getOrElse(DefenceTowerReady),
          isUp                 = up,
          commandCenterName    = n,
          level                = defenceTowersStatus.get(d.name).map(_.level).getOrElse(0),
          lastMessageTimestamp = Some(t)

        )
    }

    val now = timeProvider()
    import scala.concurrent.duration._
    val effectivePersistenceState: PersistenceState = if (persistenceState.timestamp < now - (20 seconds).toMillis) {
      PersistenceUnknown
    } else {
      persistenceState
    }
    val warTheater = WarTheater(
      defence          = towerUp,
      city             = cities,
      alienWeapons     = alienMissiles,
      humanWeapons     = humanMissiles,
      landScape        = landscape.getOrElse(LandScape(100, 100, 50)),
      commandCentres   = commandCenters.values.toList,
      explosions       = currentExplosions.map(e => ExplosionEvent(e, 1f)) :::
        previousExplosions.map(x => ExplosionEvent(x._1, 1 - (now - x._2) / explosionsDuration.toFloat)),
      points           = points,
      clusterLeader    = Cluster(context.system).state.leader.map(_.toString),
      persistenceState = effectivePersistenceState
    )
    sender ! warTheater
  }

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    import pl.project13.scala.rainbow.Rainbow._
    println("Starting".green + " StatusKeeper  ".yellow)
    Cluster(context.system).subscribe(self, ClusterEvent.InitialStateAsEvents,
      classOf[MemberUp],
      classOf[MemberRemoved],
      classOf[UnreachableMember],
      classOf[ReachableMember])
  }

  val explosionFilter: ((Explosion, Long)) => Boolean = {
    x => timeProvider() - x._2 < explosionsDuration
  }
}

object StatusKeeper {

  val defaultTimeProvider = new (() => Long) {
    override def apply(): Long = System.currentTimeMillis()
  }

  def props(timeProvider: () => Long = defaultTimeProvider): Props = Props(classOf[StatusKeeper], timeProvider)

  case object Protocol {

    case object UpdateRequest

    case class TowerKeepAlive(id: String, commandCenterName: String, towerState: DefenceTowerState, level: Int)

  }

}
