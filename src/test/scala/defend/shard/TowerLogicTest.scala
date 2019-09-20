package defend.shard

import java.time.LocalDateTime

import defend.model.{ AlienBomb, AlienEmp, DefenceTower, LandScape, MoveVector, Position, WeaponInAction }
import defend.shard.TowerActor.Protocol.{ ExperienceGained, MessageOfDeath, Ping, Situation }
import defend.shard.TowerLogic.{ FireRocket, SendKeepAlive, TowerInfected, TowerReady, TowerReloading, TowerState }
import org.scalatest.{ Matchers, WordSpec }

class TowerLogicTest extends WordSpec with Matchers {

  val initialState = TowerState(me = None)
  val me: DefenceTower = DefenceTower("a", Position(50, 50))
  val situation = Situation(
    index     = 1,
    me        = me,
    target    = List.empty,
    landScape = LandScape(100, 100, 50))

  private val now = LocalDateTime.now()
  "Tower logic " should {
    "update experience" in {
      val (newState, actions) = TowerLogic.process(ExperienceGained(10), now).run(initialState).value
      newState shouldBe initialState.copy(experience = 10)
      actions shouldBe List.empty
    }

    "update self data after receiving situation" in {
      val (newState, actions) = TowerLogic.process(situation, now).run(initialState).value
      newState.me shouldBe Some(me)
      newState.lastMessageId shouldBe situation.index
      actions shouldBe empty
    }

    "fire, change to reloading and schedule reload" in {
      val weaponInAction = WeaponInAction(AlienBomb(10, 10), Position(50, 70), MoveVector(Math.PI, 1))
      val situationUnderAttack = situation.copy(target = List(weaponInAction))
      val (newState, actions) = TowerLogic.process(situationUnderAttack, now).run(initialState).value
      newState.towerCondition shouldBe TowerReloading
      newState.scheduledStateChangeAt shouldBe defined
      val fire = actions.head
      fire match {
        case FireRocket(humanWeapon, _, defenceTower, _) =>
          humanWeapon.towerName shouldBe defenceTower.name
          defenceTower.name shouldBe me.name
        case _ => fail("Rocket not fired")
      }

      fail("should have notify statusKeeper DefenceTowerStatus")

    }
    "dont fire if reloading" in {
      val weaponInAction = WeaponInAction(AlienBomb(10, 10), Position(50, 70), MoveVector(Math.PI, 1))
      val situationUnderAttack = situation.copy(target = List(weaponInAction))
      val (newState, actions) = TowerLogic.process(situationUnderAttack, now).run(initialState.copy(towerCondition = TowerReloading)).value
      newState.towerCondition shouldBe TowerReloading
      actions shouldBe 'empty
    }
    "dont fire if there is no target" in {
      val (newState, actions) = TowerLogic.process(situation, now).run(initialState).value
      newState.towerCondition shouldBe TowerReady
      actions shouldBe 'empty
    }

    "change to ready when reloaded" in {
      val state = initialState.copy(towerCondition         = TowerReloading, scheduledStateChangeAt = Some(LocalDateTime.MIN))
      val (newState, actions) = TowerLogic.process(situation, now).run(state).value
      newState.towerCondition shouldBe TowerReady
      actions shouldBe 'empty
    }
    "heal infected after sick duration" in {
      val state = initialState.copy(towerCondition         = TowerInfected, scheduledStateChangeAt = Some(LocalDateTime.MIN))
      val (newState, actions) = TowerLogic.process(situation, now).run(state).value
      newState.towerCondition shouldBe TowerReady
      actions shouldBe 'empty
    }

    "send keepalive on ping" in {
      val (newState, actions) = TowerLogic.process(Ping, now).run(initialState).value
      newState shouldBe initialState
      actions shouldBe List(SendKeepAlive)
    }

    "get sick after infected and schedule healing after infected" in {
      val (newState, actions) = TowerLogic.process(MessageOfDeath(AlienEmp(100, 100)), now).run(initialState).value
      newState.towerCondition shouldBe TowerInfected
      newState.scheduledStateChangeAt shouldBe defined
      actions shouldBe 'empty
    }
  }
}
