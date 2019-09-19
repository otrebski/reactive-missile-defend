package defend.shard

import java.time.LocalDateTime

import defend.model.{AlienBomb, DefenceTower, LandScape, MoveVector, Position, WeaponInAction}
import defend.shard.TowerActor.Protocol.{ExperienceGained, Ping, Situation}
import defend.shard.TowerLogic.{FireRocket, SendKeepAlive, TowerReloading, TowerState}
import org.scalatest.{Matchers, WordSpec}


class TowerLogicTest extends WordSpec with Matchers {

  val initialState = TowerState(me = None)
  val me: DefenceTower = DefenceTower("a", Position(50, 50))
  val situation = Situation(
    index = 1,
    me = me,
    target = List.empty,
    landScape = LandScape(100, 100, 50)
  )

  val now = LocalDateTime.now()
  "Tower logic " should {
    "update experience" in {
      val (newState, actions) = TowerLogic.process(ExperienceGained(10), now).run(initialState).value
      newState shouldBe initialState.copy(experience = 10)
      actions shouldBe List.empty
    }
    "schedule state change" in {
      fail("Not implemented")
    }
    "update self data after receiving situation" in {

      val (newState, actions) = TowerLogic.process(situation, now).run(initialState).value
      newState.me shouldBe Some(me)
      newState.lastMessageId shouldBe situation.index
      actions shouldBe empty
    }
    "fire and change to reloading" in {
      val weaponInAction = WeaponInAction(AlienBomb(10, 10), Position(50, 70), MoveVector(Math.PI, 1))
      val situationUnderAttack = situation.copy(target = List(weaponInAction))
      val (newState, actions) = TowerLogic.process(situationUnderAttack, now).run(initialState).value
      newState.towerCondition shouldBe TowerReloading
      //TODO validate newState.scheduledStateChangeAt
      val fire = actions.head
      fire match {
        case FireRocket(humanWeapon, _, defenceTower, _) =>
          humanWeapon.towerName shouldBe defenceTower.name
          defenceTower.name shouldBe me.name
        case _ => fail("Rocket not fired")
      }

    }
    "dont fire if reloading" in {
      fail("Not implemented")
    }
    "dont fire if there is no target" in {
      fail("Not implemented")
    }
    "change to ready when reloaded" in {
      fail("Not implemented")
    }
    "heal infected after sick duration" in {
      fail("Not implemented")
    }

    "send keepalive on ping" in {
      val (newState, actions) = TowerLogic.process(Ping, now).run(initialState).value
      newState shouldBe initialState
      actions shouldBe List(SendKeepAlive)
    }

    "get sick after infected" in {
      fail("Not implemented")
    }
    "schedule reload after firing" in {
      fail("Not implemented")
    }
    "schedule healing after infected" in {
      fail("Not implemented")
    }
  }
}
