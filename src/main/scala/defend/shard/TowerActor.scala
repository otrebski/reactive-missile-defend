package defend.shard

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.{ ActorRef, DiagnosticActorLogging, Props }
import akka.cluster.Cluster
import akka.event.Logging.MDC
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import defend._
import defend.model._
import defend.shard.FsmProtocol.{ AddExperience, DomainEvent, ScheduledStateChangeAgUpdate, UpdateSelf }
import defend.shard.TowerActor.Protocol.{ MessageOfDeath, Ping, Reloaded, Situation }
import defend.ui.StatusKeeper
import defend.ui.StatusKeeper.Protocol.TowerKeepAlive
import pl.project13.scala.rainbow.Rainbow._

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.Random

sealed trait TowerFsmState extends FSMState

case object FsmReady extends TowerFsmState {
  override def identifier: String = "ready"
}

case object FsmInfected extends TowerFsmState {
  override def identifier: String = "infected"
}

case object FsmReloading extends TowerFsmState {
  override def identifier: String = "reloading"
}

case class TowerFsMData(
  me:                     Option[DefenceTower],
  experience:             Int                  = 0,
  lastMessageId:          Int                  = 0,
  scheduledStateChangeAt: Option[Long]         = None
)

object FsmProtocol {

  sealed trait DomainEvent

  case class AddExperience(exp: Int, date: Date) extends DomainEvent

  case class ScheduledStateChangeAgUpdate(at: Option[Long]) extends DomainEvent

  case class UpdateSelf(id: Int, me: Option[DefenceTower]) extends DomainEvent

}

class TowerActor(name: String, statusKeeper: ActorRef, reloadTime: FiniteDuration)(implicit val domainEventClassTag: ClassTag[FsmProtocol.DomainEvent])
    extends PersistentFSM[TowerFsmState, TowerFsMData, FsmProtocol.DomainEvent]
    with DiagnosticActorLogging {

  override val log = akka.event.Logging(this)
  val nextLevelReduction = 0.05
  val commandCenterName = Cluster(context.system).selfAddress.toString
  private val timeFormat = new SimpleDateFormat("HH:mm:ss")
  private val created = System.currentTimeMillis()

  @throws[Exception](classOf[Exception]) override def preStart(): Unit = {
    log.setMDC(mdc(()))
    log.warning(s"Starting $persistenceId on $commandCenterName")
    println(s"Starting $persistenceId on $commandCenterName".white.onBlue)
    super.preStart()
    log.clearMDC()
  }

  override def onRecoveryCompleted(): Unit = {
    log.setMDC(mdc(()))
    val recoveryTime: Long = System.currentTimeMillis() - created
    log.warning(s"Recovery completed for $persistenceId on $commandCenterName in ${recoveryTime}ms")
    println(s"Recovery completed for $persistenceId on $commandCenterName ${recoveryTime}ms".white.onBlue)
    super.preStart()
    log.clearMDC()
  }

  override protected def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
    super.onRecoveryFailure(cause, event)
    log.setMDC(mdc(()))
    println(s"Recovery failed for $persistenceId on $commandCenterName: ${cause.getMessage}".red)
    log.error(cause, s"Recovery failed for $persistenceId on $commandCenterName on event $event")
    log.clearMDC()
  }

  def persistenceId: String = {
    name
    //    self.path.parent.name
  }

  override def applyEvent(domainEvent: DomainEvent, currentData: TowerFsMData): TowerFsMData = domainEvent match {
    case AddExperience(exp, date) =>
      log.info(s"Gained $exp experience points at ${timeFormat.format(date)}")
      currentData.copy(experience = currentData.experience + exp)
    case ScheduledStateChangeAgUpdate(at) =>
      log.info("Scheduled state change")
      currentData.copy(scheduledStateChangeAt = at)
    case UpdateSelf(index, me) =>
      log.info(s"Updating self to $me")
      currentData.copy(me = me, lastMessageId = index)
  }

  startWith(FsmReady, TowerFsMData(None, 0, 0, None))

  when(FsmReady) {
    case Event(situation: Situation, data) =>
      log.info(s"Received situation ${situation.index}")
      val level = experienceToLevel(data.experience)
      val speed: Double = 70 + level * 30 + Random.nextInt(30)
      val range: Double = rangeForLevel(experienceToLevel(data.experience))
      val s = situation
      val target: Option[WeaponInAction[AlienWeapon]] = findMissileToIntercept(s.target, s.landScape, s.me.position, speed, range)
      log.debug(s"Enemy rocket to intercept: $target")
      if (target.isDefined) {
        val explosionRadius = 10 + 2 * level
        fireMissile(situation.me, target.get, speed, range, explosionRadius)
          .map { r =>
            val directionError = angleErrorForLevel(level) * Random.nextDouble()
            r.copy(moveVector = r.moveVector.copy(direction = r.moveVector.direction + directionError).inAngleDegrees(5, 175))
          }
          .foreach(sender() ! _)
        val reducedReloadTime: Double = reduceByLevel(reloadTime.toMillis, level, nextLevelReduction)
        val reloadAt: Option[Long] = Some(System.currentTimeMillis() + (reducedReloadTime / 2 + Random.nextInt(reducedReloadTime.toInt / 2)).toLong)
        goto(FsmReloading) applying ScheduledStateChangeAgUpdate(reloadAt)
      } else {
        if (data.me.contains(situation.me)) {
          stay()
        } else {
          stay() applying UpdateSelf(situation.index, Some(situation.me))
        }
      }
  }

  when(FsmReloading) {
    case Event(Reloaded, data) =>
      log.info(s"Tower ${data.me} reloaded")
      goto(FsmReady)
    case Event(s: Situation, data) =>
      log.info(s"Received situation ${s.index}")
      if (data.me.contains(s.me)) {
        stay()
      } else {
        stay() applying UpdateSelf(s.index, Some(s.me))
      }
  }

  when(FsmInfected) {
    case Event(_, TowerFsMData(_, _, _, Some(ts))) if ts < System.currentTimeMillis() =>
      goto(FsmReady) applying ScheduledStateChangeAgUpdate(None)
  }

  whenUnhandled {
    case Event(Ping, data) =>
      log.debug("Ping received in {}, status keeper is {}", stateName, statusKeeper)

      val state = stateName match {
        case FsmReloading => DefenceTowerReloading
        case FsmReady     => DefenceTowerReady
        case FsmInfected  => DefenceTowerInfected
      }
      val name: String = data.me.map(_.name).getOrElse("?")
      val keepAlive: TowerKeepAlive = StatusKeeper.Protocol.TowerKeepAlive(name, commandCenterName, towerState = state, experienceToLevel(stateData.experience))
      log.debug("Sending keep alive from {} with {}: {}", name, state, keepAlive)
      statusKeeper ! keepAlive
      stay()
    case Event(TowerActor.Protocol.ExperienceGained(exp), data) => stay() applying AddExperience(exp, new Date())
    case Event(MessageOfDeath(alienEmp, count), data) =>
      goto(FsmInfected) applying ScheduledStateChangeAgUpdate(Some(System.currentTimeMillis() + count * 1000))
    case Event(s: Situation, data) =>
      log.info(s"Received situation ${s.index}")
      if (data.me.contains(s.me)) {
        stay()
      } else {
        stay() applying UpdateSelf(s.index, Some(s.me))
      }

    case a: Any =>
      log.debug("Unhandled message {}", a)
      stay()
  }

  onTransition {
    case FsmReady -> FsmReloading =>
      log.debug("Going to -> Reloading")
      implicit val ec = scala.concurrent.ExecutionContext.global
      context.system.scheduler.scheduleOnce(reloadTime, self, Reloaded)
      stateData.me.foreach(d =>
        statusKeeper ! DefenceTowerStatus(d, isUp = true, defenceTowerState = DefenceTowerReloading, commandCenterName = Some(commandCenterName), level = experienceToLevel(stateData.experience)))
    case _ -> FsmReady =>
      log.debug("Going to -> Ready")
      stateData.me.foreach(d => statusKeeper ! DefenceTowerStatus(d, isUp = true, defenceTowerState = DefenceTowerReady, commandCenterName = Some(commandCenterName), level = experienceToLevel(stateData.experience)))
    case _ -> FsmInfected =>
      log.debug(s"Going to -> Infected until ${nextStateData.scheduledStateChangeAt}")
      stateData.me.foreach(d => statusKeeper ! DefenceTowerStatus(d, isUp = true, defenceTowerState = DefenceTowerInfected, commandCenterName = Some(commandCenterName), level = experienceToLevel(stateData.experience)))
  }

  override def mdc(currentMessage: Any): MDC = {
    Map[String, Any]("tower" -> persistenceId, "node" -> commandCenterName)
  }

  override def postStop(): Unit = {
    log.setMDC(mdc(()))
    log.warning(s"Stopping $persistenceId on $commandCenterName")
    println(s"Stopping $persistenceId on $commandCenterName".white.onBlue)
    super.postStop()
    log.clearMDC()
  }

}

object TowerActor {

  import scala.concurrent.duration._

  def props(name: String, statusKeeper: ActorRef, reloadTime: FiniteDuration = 2 seconds): Props = Props(new TowerActor(name, statusKeeper, reloadTime))

  object Protocol {

    case object Reloaded

    case object Ping

    case class ExperienceGained(exp: Int)

    case class Situation(index: Int, me: DefenceTower, target: List[WeaponInAction[AlienWeapon]], landScape: LandScape)

    case class MessageOfDeath(alienEmp: AlienEmp, secondsToCure: Int = 4)

  }

}
