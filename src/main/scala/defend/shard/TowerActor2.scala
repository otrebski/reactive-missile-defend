package defend.shard

import java.time.LocalDateTime
import java.time.{Duration => JDuration}

import akka.actor.ActorRef
import akka.persistence.PersistentActor
import cats.data.State
import defend.model.{AlienWeapon, DefenceTower, HumanWeapon, MoveVector, WeaponInAction}
import defend.shard.TowerActor.Protocol._
import defend.shard.TowerLogic.TowerState

import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps
import cats.syntax.option._
import defend.game.GameEngine.Protocol
import defend.{experienceToLevel, findMissileToIntercept, fireMissile, rangeForLevel}

class TowerActor2(name: String, statusKeeper: ActorRef, reloadTime: FiniteDuration) extends PersistentActor {

  var towerState = TowerState(None, 0, 0)

  override def receiveRecover: Receive = {
    case msg: ActorMsg =>
      val (newState, _) = TowerLogic.process(msg, LocalDateTime.now()).run(towerState).value
      towerState = newState
  }

  override def receiveCommand: Receive = {
    case msg: ActorMsg =>
      val (newState, actions) = TowerLogic.process(msg, LocalDateTime.now()).run(towerState).value
      towerState = newState
      actions foreach println
  }

  override def persistenceId: String = name
}

object TowerLogic {

  sealed trait TowerCondition

  case object TowerReady extends TowerCondition

  case object TowerInfected extends TowerCondition

  case object TowerReloading extends TowerCondition

  case class TowerState(me: Option[DefenceTower],
                        experience: Int = 0,
                        lastMessageId: Int = 0,
                        reloadTime: FiniteDuration = 100 millis,
                        scheduledStateChangeAt: Option[LocalDateTime] = None,
                        towerCondition: TowerCondition = TowerReady)

  sealed trait ActionResult

  case object SendKeepAlive extends ActionResult

  case class FireRocket(humanWeapon: HumanWeapon, moveVector: MoveVector, defenceTower: DefenceTower, explodingVelocity: Option[Double]) extends ActionResult

  case object NotifyStatusKeeper extends ActionResult


  type TowerSm = State[TowerState, List[ActionResult]]

  def fire(s: Situation, now: LocalDateTime) = State[TowerState, Option[FireRocket]] { state =>
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
            me = s.me.some,
            towerCondition = TowerReloading,
            scheduledStateChangeAt = now.plus(JDuration.ofMillis(state.reloadTime.toMillis)).some
          ),
          Some(
            FireRocket(
              humanWeapon = missile.humanWeapon,
              moveVector = missile.moveVector,
              defenceTower = missile.defenceTower,
              explodingVelocity = missile.explodingVelocity
            )
          ))
      case None => (state.copy(me = s.me.some), none[FireRocket])
    }
  }

  val ping = State[TowerState, List[ActionResult]](state => (state, List(SendKeepAlive)))

  def updateSelfOnSituation(s: Situation) = State[TowerState, List[ActionResult]](state =>
    (state.copy(me = s.me.some, lastMessageId = s.index), List.empty)
  )

  val noAction = State[TowerState, List[ActionResult]](state => (state, List.empty))

  def process(msg: ActorMsg, now: LocalDateTime): TowerSm = State[TowerState, List[ActionResult]] { state =>
    val p = msg match {
      case Ping => ping
      case ExperienceGained(exp) =>
        val newState = state.copy(experience = state.experience + exp)
        State.set(newState).map(_ => List.empty[ActionResult])
      case MessageOfDeath(_, secondsToCure) =>
        val changeAt = LocalDateTime.now().plus(JDuration.ofSeconds(secondsToCure))
        val newState = state.copy(scheduledStateChangeAt = Some(changeAt))
        (newState, List.empty[ActionResult])
        State.set(newState).map(_ => List.empty[ActionResult])
      case _ =>
        state.towerCondition match {
          case TowerReady =>
            msg match {
              case situation: Situation =>
                for {
                  _ <- updateSelfOnSituation(situation)
                  maybeFire <- fire(situation, now)
                } yield maybeFire.toList
            }
          case TowerReloading =>
            noAction
          case TowerInfected =>
            noAction
        }
    }
    p.run(state).value
  }
}