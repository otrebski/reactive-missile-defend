package defend.shard

import java.time.LocalDateTime
import java.time.{ Duration => JDuration }

import akka.actor.{ ActorRef, Props }
import akka.cluster.Cluster
import akka.persistence.{ PersistentActor, RecoveryCompleted }
import cats.data.State
import defend.model.{ AlienWeapon, DefenceTower, DefenceTowerInfected, DefenceTowerReady, DefenceTowerReloading, HumanWeapon, MoveVector, WeaponInAction }
import defend.shard.TowerActor.Protocol._
import defend.shard.TowerLogic.{ FireRocket, SendKeepAlive, TowerReady, TowerReloading, TowerState }

import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.language.postfixOps
import cats.syntax.option._
import defend.game.GameEngine
import defend.game.GameEngine.Protocol
import defend.ui.StatusKeeper
import defend.ui.StatusKeeper.Protocol.TowerKeepAlive
import defend.{ experienceToLevel, findMissileToIntercept, fireMissile, rangeForLevel }

class TowerActor2(name: String, statusKeeper: ActorRef, reloadTime: FiniteDuration) extends PersistentActor {

  val created: Long = System.currentTimeMillis()
  var towerState = TowerState(me = None)
  val commandCenterName: String = Cluster(context.system).selfAddress.toString

  override def receiveRecover: Receive = {
    case msg: ActorMsg =>
      val (newState, _) = TowerLogic.process(msg, LocalDateTime.now()).run(towerState).value
      towerState = newState
    case RecoveryCompleted =>
      val recoveryTime: Long = System.currentTimeMillis() - created
      statusKeeper ! StatusKeeper.Protocol.RecoveryReport(persistenceId, recoveryTime, success = true)
  }

  override def receiveCommand: Receive = {
    case msg: ActorMsg =>
      val (newState, actions) = TowerLogic.process(msg, LocalDateTime.now()).run(towerState).value
      if (towerState.towerCondition == TowerReloading && newState.towerCondition == TowerReady) {
        println(s"Tower ${towerState.me} reloaded")
      }
      if (towerState.towerCondition == TowerReady && newState.towerCondition == TowerReloading) {
        println(s"Tower ${towerState.me} is reloading")
      }

      towerState = newState
      actions.foreach {
        case SendKeepAlive =>
          val defenceTowerState = towerState.towerCondition match {
            case TowerLogic.TowerReady     => DefenceTowerReady
            case TowerLogic.TowerReloading => DefenceTowerReloading
            case TowerLogic.TowerInfected  => DefenceTowerInfected
          }
          val keepAlive: TowerKeepAlive = StatusKeeper.Protocol.TowerKeepAlive(
            id                = name,
            commandCenterName = commandCenterName,
            towerState        = defenceTowerState,
            level             = experienceToLevel(towerState.experience))
          statusKeeper ! keepAlive

        case FireRocket(humanWeapon, moveVector, defenceTower, explodingVelocity) =>
          sender() ! GameEngine.Protocol.RocketFired(humanWeapon, moveVector, defenceTower, explodingVelocity)
      }
  }

  override def persistenceId: String = name
}

object TowerActor2 {
  def props(name: String, statusKeeper: ActorRef, reloadTime: FiniteDuration = 2 seconds): Props =
    Props(new TowerActor2(name, statusKeeper, reloadTime))

}

object TowerLogic {

  sealed trait TowerCondition

  case object TowerReady extends TowerCondition

  case object TowerInfected extends TowerCondition

  case object TowerReloading extends TowerCondition

  case class TowerState(
    me:                     Option[DefenceTower],
    experience:             Int                   = 0,
    lastMessageId:          Int                   = 0,
    reloadTime:             FiniteDuration        = 1 second,
    scheduledStateChangeAt: Option[LocalDateTime] = None,
    towerCondition:         TowerCondition        = TowerReady)

  sealed trait ActionResult

  case object SendKeepAlive extends ActionResult

  case class FireRocket(humanWeapon: HumanWeapon, moveVector: MoveVector, defenceTower: DefenceTower, explodingVelocity: Option[Double]) extends ActionResult

  case class SendTowerStatus(defenceTower: DefenceTower, towerCondition: TowerCondition) extends ActionResult

  type TowerSm = State[TowerState, List[ActionResult]]

  private def fire(s: Situation, now: LocalDateTime) = State[TowerState, Option[FireRocket]] { state =>
    val level = experienceToLevel(state.experience)
    val speed: Double = 70 + level * 30
    val range: Double = rangeForLevel(experienceToLevel(state.experience))
    val maybeTarget: Option[WeaponInAction[AlienWeapon]] = findMissileToIntercept(s.target, s.landScape, s.me.position, speed, range)

    val fired: Option[Protocol.RocketFired] = for {
      target <- maybeTarget
      explosionRadius = 10 + 2 * level
      firedMissile <- fireMissile(s.me, target, speed, range, explosionRadius)
    } yield firedMissile
    fired match {
      case Some(missile) =>
        (
          //Schedule reload
          state.copy(
            me                     = s.me.some,
            towerCondition         = TowerReloading,
            scheduledStateChangeAt = now.plus(JDuration.ofMillis(state.reloadTime.toMillis)).some),
          Some(
            FireRocket(
              humanWeapon       = missile.humanWeapon,
              moveVector        = missile.moveVector,
              defenceTower      = missile.defenceTower,
              explodingVelocity = missile.explodingVelocity)))
      case None => (state.copy(me = s.me.some), none[FireRocket])
    }
  }

  private val ping = State[TowerState, List[ActionResult]](state => (state, List(SendKeepAlive)))

  private def updateSelfOnSituation(s: Situation) = State[TowerState, List[ActionResult]](state =>
    (state.copy(me            = s.me.some, lastMessageId = s.index), List.empty))

  private val noAction = State[TowerState, List[ActionResult]](state => (state, List.empty))

  def process(msg: ActorMsg, now: LocalDateTime): TowerSm = State[TowerState, List[ActionResult]] { state =>
    val p = msg match {
      case Ping => ping
      case ExperienceGained(exp) =>
        val newState = state.copy(experience = state.experience + exp)
        State.set(newState).map(_ => List.empty[ActionResult])
      case MessageOfDeath(_, secondsToCure) =>
        val changeAt = LocalDateTime.now().plus(JDuration.ofSeconds(secondsToCure))
        val newState = state.copy(towerCondition         = TowerInfected, scheduledStateChangeAt = Some(changeAt))
        (newState, List.empty[ActionResult])
        State.set(newState).map(_ => state.me.map(me => SendTowerStatus(me, TowerInfected)).toList)
      case _ =>
        state.towerCondition match {
          case TowerReady =>
            msg match {
              case situation: Situation =>
                for {
                  _ <- updateSelfOnSituation(situation)
                  maybeFire <- fire(situation, now)

                } yield {
                  val maybeStatus = state.me.map(me => SendTowerStatus(me, TowerReloading))
                  maybeFire.toList ::: maybeStatus.toList
                }
              case _ => noAction
            }
          case TowerReloading =>
            if (state.scheduledStateChangeAt.exists(_.isBefore(now))) {
              State.set(state.copy(towerCondition         = TowerReady, scheduledStateChangeAt = None)).map(_ => state.me.map(me => SendTowerStatus(me, TowerLogic.TowerReady)).toList)
            } else {
              noAction
            }
          case TowerInfected =>
            if (state.scheduledStateChangeAt.exists(_.isBefore(now))) {
              State.set(state.copy(towerCondition         = TowerReady, scheduledStateChangeAt = None)).map(_ => state.me.map(me => SendTowerStatus(me, TowerLogic.TowerReady)).toList)

            } else {
              noAction
            }

        }
    }
    p.run(state).value
  }
}