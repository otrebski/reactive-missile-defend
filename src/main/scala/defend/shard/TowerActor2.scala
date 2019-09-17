package defend.shard

import akka.actor.ActorRef
import akka.persistence.PersistentActor
import cats.data.State
import defend.model.{DefenceTower, HumanWeapon, MoveVector}
import defend.shard.TowerActor.Protocol._
import defend.shard.TowerLogic.TowerState

import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps
import cats.syntax.option._

class TowerActor2(name: String, statusKeeper: ActorRef, reloadTime: FiniteDuration) extends PersistentActor {

  var towerState = TowerState(None, 0, 0)

  override def receiveRecover: Receive = {
    case msg: ActorMsg =>
      val (newState, _) = TowerLogic.process(msg).run(towerState).value
      towerState = newState
  }

  override def receiveCommand: Receive = {
    case msg: ActorMsg =>
      val (newState, actions) = TowerLogic.process(msg).run(towerState).value
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
                        scheduledStateChangeAt: Option[Long] = None,
                        towerCondition: TowerCondition = TowerReady)

  sealed trait ActionResult

  case object SendKeepAlive extends ActionResult

  case class FireRocket(humanWeapon: HumanWeapon, moveVector: MoveVector, defenceTower: DefenceTower, explodingVelocity: Option[Double]) extends ActionResult

  case object NotifyStatusKeeper extends ActionResult


  type TowerSm = State[TowerState, List[ActionResult]]

  def fire(s: Situation) = State[TowerState, Option[FireRocket]] { state =>
    (state, Option.empty[FireRocket])
  }

  val ping = State[TowerState, List[ActionResult]](state => (state, List(SendKeepAlive)))

  def updateSelfOnSituation(s: Situation) = State[TowerState, List[ActionResult]](state =>
    (state.copy(me = s.me.some, lastMessageId = s.index), List.empty)
  )

  val noAction =  State[TowerState, List[ActionResult]](state => (state, List.empty))

  def process(msg: ActorMsg): TowerSm = State[TowerState, List[ActionResult]] { state =>
    val p = msg match {
      case Ping => ping
      case ExperienceGained(exp) =>
        val newState = state.copy(experience = state.experience + exp)
        State.set(newState).map(_ => List.empty[ActionResult])
      case MessageOfDeath(_, secondsToCure) =>
        val newState = state.copy(scheduledStateChangeAt = Some(System.currentTimeMillis() + 1000 * secondsToCure))
        (newState, List.empty[ActionResult])
        State.set(newState).map(_ => List.empty[ActionResult])
      case _ =>
        state.towerCondition match {
          case TowerReady =>
            msg match {
              case situation: Situation =>
                for {
                  _ <- updateSelfOnSituation(situation)
                  maybeFire <- fire(situation)
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