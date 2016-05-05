package defend.ui

import akka.actor.{ ActorRef, ActorSystem, Terminated }
import akka.testkit.{ TestKit, TestProbe }
import com.typesafe.config.ConfigFactory
import defend.model._
import org.scalatest._

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.language.postfixOps

class StatusKeeperTest extends TestKit(ActorSystem("defend", ConfigFactory.load("application-test.conf"))) with FlatSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {
  val timeout: FiniteDuration = 1 second
  val landscape = LandScape(100, 100, 50)
  val emptyWarTheater = WarTheater(
    defence      = List.empty,
    city         = List.empty,
    alienWeapons = List.empty,
    humanWeapons = List.empty,
    landscape,
    List.empty
  )

  val alienWeapons = List(
    WeaponInAction(AlienBomb(10, 10), Position(400, 200), MoveVector(Math.PI / 2, 1)),
    WeaponInAction(AlienNuke(100, 10), Position(350, 430), MoveVector(Math.PI / 2, 1)),
    WeaponInAction(AlienMissile(100, 10), Position(610, 60), MoveVector(Math.PI * 1.25, 1)),
    WeaponInAction(AlienEmp(100, 10), Position(150, 350), MoveVector(Math.PI * 0.25, 2))
  )

  val humanWeapons1 = List(
    WeaponInAction(HumanMissile("1", 10, 20), Position(200, 400), MoveVector(Math.PI * -0.5, 6)),
    WeaponInAction(HumanMissile("2", 10, 20), Position(220, 400), MoveVector(Math.PI * -0.5, 8))
  )

  val humanWeapons2 = List(
    WeaponInAction(HumanMissile("3", 10, 20), Position(240, 400), MoveVector(Math.PI * -0.5, 10)),
    WeaponInAction(HumanMissile("4", 10, 20), Position(260, 400), MoveVector(Math.PI * -0.5, 12))
  )

  val cities = List(
    City("a", Position(1, 2), 0),
    City("b", Position(1, 2), 0),
    City("c", Position(1, 2), 0)
  )

  val defence = List(
    DefenceTower("A", Position(1, 1)),
    DefenceTower("B", Position(2, 1)),
    DefenceTower("C", Position(3, 1))
  )

  var underTest: ActorRef = _
  var probe: TestProbe = _

  override protected def beforeEach(): Unit = {
    underTest = system.actorOf(StatusKeeper.props())
    probe = new TestProbe(system)
  }

  "StatusKeeper" should "send empty status on start" in {

    underTest.tell(StatusKeeper.Protocol.UpdateRequest, probe.ref)

    probe.expectMsgPF(1 second) {
      case s: WarTheater => s shouldBe emptyWarTheater.copy(
        timestamp     = s.timestamp,
        clusterLeader = Some("akka.tcp://defend@localhost:12500"),
        statusKeeper  = s.statusKeeper
      )
    }
  }

  //TODO mock member up/leaving
  it should "update cluster state" ignore {
    val underTest: ActorRef = system.actorOf(StatusKeeper.props())
    val probe: TestProbe = new TestProbe(system)

    //    underTest ! MemberUp(Member())
    //    underTest ! MemberUp(Member())
    //    underTest ! MemberLeaving(Member())
    //    underTest ! MemberUp(Member())
    underTest.tell(StatusKeeper.Protocol.UpdateRequest, probe.ref)

    probe.expectMsgPF(timeout) {
      case m: WarTheater =>
        m.commandCentres.size shouldBe 2
    }
  }

  it should "update human missiles" in {
    underTest ! OptionalWarTheater(humanWeapons = Some(humanWeapons1))
    underTest.tell(StatusKeeper.Protocol.UpdateRequest, probe.ref)
    probe.expectMsgPF(timeout) {
      case w: WarTheater =>
        w.humanWeapons.size shouldBe 2
        w.humanWeapons.map(_.weapon.towerName) shouldBe List("1", "2")
    }

    underTest ! OptionalWarTheater(humanWeapons = Some(humanWeapons2))
    underTest.tell(StatusKeeper.Protocol.UpdateRequest, probe.ref)
    probe.expectMsgPF(timeout) {
      case w: WarTheater =>
        w.humanWeapons.size shouldBe 2
        w.humanWeapons.map(_.weapon.towerName) shouldBe List("3", "4")
    }
  }

  it should "update alien missiles" in {
    underTest ! OptionalWarTheater(alienWeapons = Some(alienWeapons))

    underTest.tell(StatusKeeper.Protocol.UpdateRequest, probe.ref)

    probe.expectMsgPF(timeout) {
      case w: WarTheater =>
        w.alienWeapons.size shouldBe 4
        w.alienWeapons shouldBe alienWeapons
    }
  }

  it should "update cities" in {
    underTest ! OptionalWarTheater(cities = Some(cities))

    underTest.tell(StatusKeeper.Protocol.UpdateRequest, probe.ref)

    probe.expectMsgPF(timeout) {
      case w: WarTheater =>
        w.city.size shouldBe 3
        w.city shouldBe cities
    }
  }

  it should "send full status on update request" in {
    underTest ! OptionalWarTheater(humanWeapons = Some(humanWeapons1))
    underTest ! OptionalWarTheater(alienWeapons = Some(alienWeapons), cities = Some(cities))
    underTest ! OptionalWarTheater(defence = Some(defence))
    underTest ! DefenceTowerStatus(defence.head, defenceTowerState = DefenceTowerReloading, isUp = false, Some("cc"), 0)
    underTest ! OptionalWarTheater(humanWeapons = Some(humanWeapons2))
    underTest ! StatusKeeper.Protocol.TowerKeepAlive(defence.head.name, "cc", DefenceTowerReloading, 10)

    underTest.tell(StatusKeeper.Protocol.UpdateRequest, probe.ref)

    probe.expectMsgPF(timeout) {
      case w: WarTheater =>
        w.alienWeapons shouldBe alienWeapons
        w.humanWeapons shouldBe humanWeapons2
        w.city shouldBe cities
        w.commandCentres shouldBe List.empty[CommandCenter]
        w.defence.head.isUp shouldBe true
        w.defence.head.level shouldBe 10
        w.defence.head.commandCenterName shouldBe Some("cc")
        w.defence.head.defenceTowerState shouldBe DefenceTowerReloading
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    println("Shutting down actor system after")
    val terminate: Future[Terminated] = system.terminate()
    implicit val ec = scala.concurrent.ExecutionContext.global
    println("Waiting for actor system to close")
    Await.result(terminate, 10 seconds)
    println("Actor system closed")
  }
}
