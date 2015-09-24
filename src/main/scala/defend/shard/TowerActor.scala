package defend.shard

import akka.actor.{ ActorRef, DiagnosticActorLogging, FSM, Props }
import akka.cluster.Cluster
import akka.cluster.sharding.ShardRegion
import akka.event.Logging.MDC
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer, SnapshotSelectionCriteria }
import com.typesafe.scalalogging.slf4j.LazyLogging
import defend._
import defend.model._
import defend.shard.TowerActor.Protocol._
import defend.ui.StatusKeeper
import pl.project13.scala.rainbow.Rainbow._

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

sealed trait TowerState

case object Ready extends TowerState

case object Infected extends TowerState

case object Reloading extends TowerState

case class TowerData(me: Option[DefenceTower], experience: Int, scheduledStateChangeAt: Option[Long] = None)

class TowerActor(
  statusKeeper: ActorRef,
  reloadTime:   FiniteDuration
)
    extends PersistentActor
    with FSM[TowerState, TowerData]
    with DiagnosticActorLogging
    with LazyLogging {

  override val log = akka.event.Logging(this)
  val nextLevelReduction = 0.05
  val commandCenterName = Cluster(context.system).selfAddress.toString

  implicit val ec = scala.concurrent.ExecutionContext.global

  private var startWithData: TowerData = TowerData(me = None, experience = 0, scheduledStateChangeAt = None)
  startWith(Ready, startWithData)

  when(Ready) {
    case Event(s: Situation, state) =>
      log.debug(s"Received situation, ${s.index} (running on $commandCenterName)")
      val level = experienceToLevel(state.experience)
      val speed: Double = 70 + level * 30 + Random.nextInt(30)
      val range: Double = rangeForLevel(experienceToLevel(state.experience))
      val target: Option[WeaponInAction[AlienWeapon]] = findMissileToIntercept(s.target, s.landScape, s.me.position, speed, range)
      log.debug(s"Enemy rocket to intercept: $target")
      if (target.isDefined) {
        val explosionRadius = 10 + 2 * level
        fireMissile(s.me, target.get, speed, range, explosionRadius)
          .map { r =>
            val directionError = angleErrorForLevel(level) * Random.nextDouble()
            r.copy(moveVector = r.moveVector.copy(direction = r.moveVector.direction + directionError).inAngleDegrees(5, 175))
          }
          .foreach(sender() ! _)
        val reducedReloadTime: Double = reduceByLevel(reloadTime.toMillis, level, nextLevelReduction)
        val reloadAt: Option[Long] = Some(System.currentTimeMillis() + (reducedReloadTime / 2 + Random.nextInt(reducedReloadTime.toInt / 2)).toLong)
        goto(Reloading) using state.copy(me = Some(s.me), scheduledStateChangeAt = reloadAt)
      } else {
        stay() using state.copy(me = Some(s.me))
      }
  }

  when(Reloading) {
    case Event(Reloaded, data) => goto(Ready)
    case Event(s: Situation, state) =>
      log.debug("Situation received when reloading")
      stay() using state.copy(me = Some(s.me))
  }

  when(Infected) {
    case Event(MessageOfDeath(alienEmp, count), data) =>
      val orElse: Long = data.scheduledStateChangeAt.getOrElse(0)
      val someString: Option[Long] = Some(orElse + count * 1000)
      stay() using data.copy(scheduledStateChangeAt = someString)
    case Event(_, td @ TowerData(_, _, Some(ts))) if ts < System.currentTimeMillis() => goto(Ready) using td.copy(scheduledStateChangeAt = None)
  }

  whenUnhandled {
    case Event(Ping, TowerData(Some(d), exp, scheduledChange)) =>
      log.debug("Ping received in {}, status keeper is {}", stateName, statusKeeper)

      val state = stateName match {
        case Reloading => DefenceTowerReloading
        case Ready     => DefenceTowerReady
        case Infected  => DefenceTowerInfected
      }
      log.debug("Sending keep alive from {} with {}", d.name, state)
      statusKeeper ! StatusKeeper.Protocol.TowerKeepAlive(d.name, commandCenterName, towerState = state, experienceToLevel(stateData.experience))

      stay()
    case Event(e @ ExperienceGained(exp), data) =>
      val newData: TowerData = data.copy(experience = data.experience + exp)
      val snapshotSelectionCriteria: SnapshotSelectionCriteria = SnapshotSelectionCriteria(maxTimestamp = System.currentTimeMillis() - 1)
      saveSnapshot(newData)
      deleteSnapshots(snapshotSelectionCriteria)
      stay() using newData
    case Event(MessageOfDeath(alienEmp, count), data) =>
      //      self ! MessageOfDeath(alienEmp, count)
      goto(Infected) using data.copy(scheduledStateChangeAt = Some(System.currentTimeMillis() + count * 1000))
    case Event(s: Situation, state) =>
      log.debug("Received situation ins state {}", stateName)
      stay() using state.copy(me = Some(s.me))
    case a: Any =>
      log.debug("Unhandled message {}", a)
      stay()
  }

  onTransition {
    case Ready -> Reloading =>
      log.debug("Going to -> Reloading")
      implicit val ec = scala.concurrent.ExecutionContext.global
      context.system.scheduler.scheduleOnce(reloadTime, self, Reloaded)
      stateData.me.foreach(d =>
        statusKeeper ! DefenceTowerStatus(d, isUp = true, defenceTowerState = DefenceTowerReloading, commandCenterName = Some(commandCenterName), level = experienceToLevel(stateData.experience)))
    case _ -> Ready =>
      log.debug("Going to -> Ready")
      stateData.me.foreach(d => statusKeeper ! DefenceTowerStatus(d, isUp = true, defenceTowerState = DefenceTowerReady, commandCenterName = Some(commandCenterName), level = experienceToLevel(stateData.experience)))
    case _ -> Infected =>
      log.debug(s"Going to -> Infected until ${startWithData.scheduledStateChangeAt}")
      stateData.me.foreach(d => statusKeeper ! DefenceTowerStatus(d, isUp = true, defenceTowerState = DefenceTowerInfected, commandCenterName = Some(commandCenterName), level = experienceToLevel(stateData.experience)))
  }

  @throws[Exception](classOf[Exception]) override def preStart(): Unit = {
    super.preStart()
    log.info(s"Starting actor $persistenceId on $commandCenterName")
    println(s"Starting tower actor $persistenceId".green)
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info(s"Stopping actor $persistenceId in $stateName on $commandCenterName")
    println(s"Stopping actor $persistenceId in $stateName with $stateData".black.onYellow)
  }

  override def receiveRecover: Receive = {
    case exp: ExperienceGained => self ! exp
    case SnapshotOffer(meta, d: TowerData) =>
      log.info(s"Recovering with snapshot $d")
      startWithData = d
    case RecoveryCompleted =>
      val msg: String = s"Tower $persistenceId has recovered. Will start with ${startWithData.experience} experience"
      log.info(msg)
      println(msg.green)
      startWith(Ready, startWithData)
    //      log.info("Calling initialize()")
    //      initialize()
    case a: Any =>
      println(s"Recovering: $persistenceId recover $a")
  }

  override def receiveCommand: Receive = {
    case a: Any => println(s"$persistenceId command $a")
  }

  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  override def mdc(currentMessage: Any): MDC = {
    Map[String, Any]("tower" -> persistenceId, "node" -> commandCenterName)
  }
}

case object TowerActor {

  object Protocol {

    case object Reloaded

    case object Ping

    case class ExperienceGained(exp: Int)

    case class Situation(index: Int, me: DefenceTower, target: List[WeaponInAction[AlienWeapon]], landScape: LandScape)

    case class MessageOfDeath(alienEmp: AlienEmp, secondsToCure: Int = 4)

  }

  case class TowerState(me: Option[DefenceTower], experience: Int = 0)

  val shardRegion = "tower"

  def props(
    statusKeeper: ActorRef,
    reloadTime:   FiniteDuration = 2 seconds
  ): Props = Props(new TowerActor(statusKeeper, reloadTime))

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case Envelope(id, payload) ⇒ (id.toString, payload)
  }

  def shardResolver(shardCount: Int = 40): ShardRegion.ExtractShardId = {
    case Envelope(id, m) => id.hashCode.%(shardCount).toString
    //      case _=> ""
  }

}

