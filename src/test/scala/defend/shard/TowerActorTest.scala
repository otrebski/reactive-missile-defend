package defend.shard

import akka.actor.{ ActorSystem, PoisonPill, Props, Terminated }
import akka.testkit.{ TestKit, TestProbe }
import com.typesafe.config.ConfigFactory
import defend.game.GameEngine.Protocol.RocketFired
import defend.model._
import defend.shard.TowerActor.Protocol.{ ExperienceGained, Ping, Situation }
import defend.ui.StatusKeeper.Protocol.{ RecoveryReport, TowerKeepAlive }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.language.postfixOps

class TowerActorTest extends TestKit(ActorSystem("defend", ConfigFactory.load("application-test.conf"))) with WordSpecLike with Matchers with BeforeAndAfterAll {

  private val tower: DefenceTower = DefenceTower("A", Position(0, 0))
  private val situation: Situation = Situation(0, tower, Nil, LandScape(200, 100, 50))
  private val situationWithIncoming: Situation = Situation(1, tower, List(WeaponInAction(AlienBomb(1, 1), Position(10, 100), MoveVector(0, 0))), LandScape(200, 100, 50))

  "TowerActor" should {

    "start with ready" in {

      val statusKeeper: TestProbe = new TestProbe(system)
      val props: Props = TowerActor.props("name", statusKeeper.ref, 100 millis)

      val underTest = system.actorOf(props)
      underTest ! Ping
      underTest ! situation
      underTest ! Ping

      statusKeeper.expectMsgPF(2 second) {
        case rr: RecoveryReport =>
          rr.success shouldBe true
      }
      statusKeeper.expectMsgPF(2 seconds) {
        case t: TowerKeepAlive =>
          t.towerState shouldBe DefenceTowerReady
        case x: Any => throw new Exception(s"Received wrong message $x")
      }

      //      underTest ! PoisonPill
    }

    "go to reloading after shooting and back to ready" in {
      val statusKeeper: TestProbe = new TestProbe(system)
      val situationSender: TestProbe = new TestProbe(system)
      val props: Props = TowerActor.props("name", statusKeeper.ref, reloadTime = 150 millis)

      val underTest = system.actorOf(props)

      statusKeeper.expectMsgPF(2 second) {
        case rr: RecoveryReport =>
          rr.success shouldBe true
      }

      underTest.tell(situationWithIncoming, situationSender.ref)
      underTest ! Ping

      statusKeeper.fishForMessage(1 second) {
        case TowerKeepAlive(tower.name, _, DefenceTowerReloading, _)     => true
        case TowerKeepAlive("?", _, DefenceTowerReloading, _)            => true
        case DefenceTowerStatus(_, DefenceTowerReloading, true, _, _, _) => true
      }

      underTest.tell(situationWithIncoming, situationSender.ref)
      underTest ! Ping

      statusKeeper.fishForMessage(1 second) {
        case TowerKeepAlive(tower.name, _, DefenceTowerReloading, _) => true
      }

      //rocket fired
      situationSender.expectMsgPF(1 second) {
        case rf: RocketFired =>
          rf.defenceTower shouldBe tower
      }

      //back to ready
      statusKeeper.fishForMessage(1 second) {
        case TowerKeepAlive(tower.name, _, DefenceTowerReady, _)     => true
        case TowerKeepAlive(tower.name, _, DefenceTowerReloading, _) => false
        case DefenceTowerStatus(_, DefenceTowerReady, true, _, _, _) => true
      }

      underTest ! PoisonPill
    }

    "accumulate experience" in {
      val statusKeeper: TestProbe = new TestProbe(system)
      val props: Props = TowerActor.props("name", statusKeeper.ref)

      val underTest = system.actorOf(props)
      statusKeeper.expectMsgPF(2 second) {
        case rr: RecoveryReport     => rr.success shouldBe true
        case st: DefenceTowerStatus => st.isUp shouldBe true
      }
      statusKeeper.expectMsgPF(2 second) {
        case rr: RecoveryReport     => rr.success shouldBe true
        case st: DefenceTowerStatus => st.isUp shouldBe true
      }

      underTest ! situation

      underTest ! ExperienceGained(10)
      val level: Int = defend.experienceToLevel(10)
      underTest ! Ping
      statusKeeper.fishForMessage(1 second) {
        case TowerKeepAlive(tower.name, _, _, `level`)     => true
        case TowerKeepAlive(tower.name, _, _, _)           => false
        case DefenceTowerStatus(_, _, true, _, `level`, _) => true
      }
      underTest ! ExperienceGained(10)
      underTest ! Ping
      val level2: Int = defend.experienceToLevel(20)
      statusKeeper.fishForMessage(1 second) {
        case TowerKeepAlive(tower.name, _, _, `level2`)     => true
        case TowerKeepAlive(tower.name, _, _, _)            => false
        case DefenceTowerStatus(_, _, true, _, `level2`, _) => true
      }
    }

  }

  override protected def afterAll(): Unit = {
    val terminate: Future[Terminated] = system.terminate()
    Await.result(terminate, 5 seconds)
  }
}
