package defend

import defend.model._
import org.scalatest.{ FlatSpec, Matchers }
import scala.math._

class package$Test extends FlatSpec with Matchers {

  val position: Position = Position(10, 10)
  val alienBomb = AlienBomb(100, 10)
  val speed: Double = 10

  "move method" should "move rocket right" in {
    //given
    val vector: MoveVector = MoveVector(0, 1)

    //when
    val newPostion: Position = move(position, vector, 2000)

    //then
    newPostion shouldBe Position(12, 10)
  }
  it should "move rocket down" in {
    //given
    val vector: MoveVector = MoveVector(Math.PI / 2, 1)

    //when
    val newPostion: Position = move(position, vector, 1000)

    //then
    newPostion shouldBe Position(10, 11)
  }

  it should "move rocket up" in {
    //given
    val vector: MoveVector = MoveVector(-Math.PI / 2, 1)

    //when
    val newPostion: Position = move(position, vector, 1000)

    //then
    newPostion shouldBe Position(10, 9)
  }
  it should "move rocket up-right" in {
    //given
    val vector: MoveVector = MoveVector(-Math.PI / 4, Math.sqrt(2))

    //when
    val newPostion: Position = move(position, vector, 1000)

    //then
    newPostion shouldBe Position(11, 9)
  }

  "close enough" should "find close position" in {
    closeEnough(Position(10, 10), Position(14, 14), 6) shouldBe true
  }

  it should "not find close position" in {
    closeEnough(Position(10, 10), Position(14, 14), 2) shouldBe false
  }

  it should "not find close position 2" in {
    closeEnough(Position(10, 10), Position(10, 14), 2) shouldBe false
  }

  "findFirstToIntercept" should "return nothing for empty list" in {
    findFirstToIntercept(List.empty[WeaponInAction[AlienWeapon]], LandScape(200, 200, 200)) shouldBe None
  }

  it should "return one if only one exist" in {
    val weaponInAction: WeaponInAction[AlienWeapon] = WeaponInAction(alienBomb, Position(10, 10), MoveVector(Math.PI / 2, 1))
    findFirstToIntercept(List(weaponInAction), LandScape(200, 200, 200)) shouldBe Some(weaponInAction)
  }

  it should "return faster target" in {
    val slowWeaponInAction: WeaponInAction[AlienWeapon] = WeaponInAction(alienBomb, Position(10, 10), MoveVector(Math.PI / 2, 1))
    val fastWeaponInAction: WeaponInAction[AlienWeapon] = WeaponInAction(alienBomb, Position(10, 10), MoveVector(Math.PI / 2, 2))
    findFirstToIntercept(List(slowWeaponInAction, fastWeaponInAction), LandScape(200, 200, 200)) shouldBe Some(fastWeaponInAction)
  }

  it should "return slower target but first on ground level" in {
    val slowWeaponInAction: WeaponInAction[AlienWeapon] = WeaponInAction(alienBomb, Position(10, 10), MoveVector(Math.PI / 2, 1))
    val fastWeaponInAction: WeaponInAction[AlienWeapon] = WeaponInAction(alienBomb, Position(10, 160), MoveVector(Math.PI / 2, 2))
    findFirstToIntercept(List(slowWeaponInAction, fastWeaponInAction), LandScape(200, 200, 200)) shouldBe Some(fastWeaponInAction)
  }

  "findCollisions" should "return empty list for empty args" in {
    findCollisions(List.empty, List.empty) shouldBe Nil
  }

  it should "find one collision" in {
    //given
    val humanWeapon: List[WeaponInAction[HumanWeapon]] = List(
      WeaponInAction(HumanMissile("a", 1, 20), Position(103, 105), MoveVector(0, 0)),
      WeaponInAction(HumanMissile("b", 1, 20), Position(200, 100), MoveVector(0, 0))

    )
    val alienWeapon: List[WeaponInAction[AlienWeapon]] = List(
      WeaponInAction(alienBomb, Position(100, 100), MoveVector(0, 0)),
      WeaponInAction(AlienEmp(20, 1), Position(200, 200), MoveVector(0, 0)),
      WeaponInAction(AlienMissile(20, 1), Position(300, 300), MoveVector(0, 0)),
      WeaponInAction(AlienNuke(20, 1), Position(400, 400), MoveVector(0, 0))
    )

    //when
    val collisions: List[(WeaponInAction[HumanWeapon], List[WeaponInAction[AlienWeapon]])] = findCollisions(humanWeapon, alienWeapon)

    //then
    collisions.size shouldEqual 1
    collisions.head._1.weapon shouldBe HumanMissile("a", 1, 20)
  }
  it should "find two collision" in {
    //given
    val humanWeapon: List[WeaponInAction[HumanWeapon]] = List(
      WeaponInAction(HumanMissile("a", 1, 20), Position(103, 105), MoveVector(0, 0)),
      WeaponInAction(HumanMissile("b", 1, 20), Position(202, 200), MoveVector(0, 0)),
      WeaponInAction(HumanMissile("c", 1, 20), Position(200, 100), MoveVector(0, 0))

    )
    val alienWeapon: List[WeaponInAction[AlienWeapon]] = List(
      WeaponInAction(AlienBomb(20, 1), Position(100, 100), MoveVector(0, 0)),
      WeaponInAction(AlienEmp(20, 1), Position(200, 200), MoveVector(0, 0)),
      WeaponInAction(AlienMissile(20, 1), Position(300, 300), MoveVector(0, 0)),
      WeaponInAction(AlienNuke(20, 1), Position(400, 400), MoveVector(0, 0))
    )

    //when
    val collisions: List[(WeaponInAction[HumanWeapon], List[WeaponInAction[AlienWeapon]])] = findCollisions(humanWeapon, alienWeapon)

    //then
    collisions.size shouldEqual 2
    collisions.head._1.weapon shouldBe HumanMissile("b", 1, 20)
    collisions(1)._1.weapon shouldBe HumanMissile("a", 1, 20)
  }

  "damageCities" should "no change if no explosion found" in {
    //given
    val cities: List[City] = List(City("A", Position(10, 10), 100))

    //when
    val result: List[City] = damageCities(cities, List.empty[Explosion])

    //then
    result.head.condition shouldBe 100
  }

  it should "destroy city" in {
    val p: Position = Position(10, 10)
    //given
    val cities: List[City] = List(City("A", p, 9))

    //when
    val result: List[City] = damageCities(cities, List(Explosion(p, AlienMissile(10, 10))))

    //then
    result.head.condition shouldBe 0
  }

  it should "reduce city health" in {
    val p: Position = Position(10, 10)
    //given
    val cities: List[City] = List(City("A", p, 100), City("B", Position(100, 10), 100))

    //when
    val result: List[City] = damageCities(cities, List(Explosion(p, AlienMissile(10, 10))))

    //then
    result.head.condition shouldBe 90
    result(1).condition shouldBe 100
  }

  "experience to level" should "be correctly translated" in {
    experienceToLevel(2) shouldBe 2
    experienceToLevel(0) shouldBe 1
    experienceToLevel(10) shouldBe 4
    experienceToLevel(16) shouldBe 5
    experienceToLevel(33) shouldBe 6
  }

  "calculateDirection" should "calculate right direction" in {
    calculateDirection(Position(100, 100), Position(200, 100)) shouldBe MoveVector(0, 100)
  }

  it should "calculate left direction" in {
    calculateDirection(Position(100, 100), Position(0, 100)) shouldBe MoveVector(Math.PI, 100)
  }

  it should "calculate down direction" in {
    calculateDirection(Position(100, 100), Position(100, 0)) shouldBe MoveVector(Math.PI * 1.5, 100)
  }

  it should "calculate up direction" in {
    calculateDirection(Position(100, 100), Position(100, 150)) shouldBe MoveVector(0.5 * Math.PI, 50)
  }

  it should "calculate up-right direction" in {
    calculateDirection(Position(100, 100), Position(150, 150)) shouldBe MoveVector(0.25 * Math.PI, sqrt(20000) / 2)
  }

  it should "calculate up-left direction" in {
    calculateDirection(Position(100, 100), Position(50, 150)) shouldBe MoveVector(0.75 * Math.PI, sqrt(20000) / 2)
  }

  it should "calculate down-left direction" in {
    calculateDirection(Position(100, 100), Position(50, 50)) shouldBe MoveVector(1.25 * Math.PI, sqrt(20000) / 2)
  }

  it should "calculate down-right direction" in {
    calculateDirection(Position(100, 100), Position(150, 50)) shouldBe MoveVector(1.75 * Math.PI, sqrt(20000) / 2)
  }

  "calculateInterceptionVector" should """calculate interception vector for situation:
    //....M
    //......\
    //.......X
    //....../
    //....T""" in {

    val interceptionVector: InterceptionData = calculateInterception(Position(0, 100), speed, Position(0, 200), MoveVector(Direction.DownRight, speed)).get
    interceptionVector.moveVector.direction.toDegrees.toInt shouldBe 45 +- 1
    interceptionVector.moveVector.speed shouldBe speed
  }
  it should """calculate interception vector for situation:
    //......M
    //..../
    //...X
    //....\
    //.....T""" in {
    val interceptionVector = calculateInterception(Position(200, 0), speed, Position(200, 200), MoveVector(Direction.DownLeft, speed)).get
    val error: Double = Math.abs(interceptionVector.moveVector.direction - Direction.UpLeft)
    interceptionVector.moveVector.direction.toDegrees.toInt shouldBe 135 +- 1
    interceptionVector.moveVector.speed shouldBe speed
  }

  it should """calculate interception vector for situation:
    //......M---X
    //........./
    //......../
    //......./
    //......T""" in {
    val interceptionVector = calculateInterception(Position(100, 0), Math.sqrt(2) * speed, Position(0, 200), MoveVector(Direction.Right, speed)).get
    interceptionVector.moveVector.direction.toDegrees.toInt shouldBe 77 +- 1
  }

  it should """calculate interception vector for situation:
    //..M-------X
    //........./
    //......../
    //......./
    //......T""" in {
    val interceptionVector = calculateInterception(Position(200, 0), Math.sqrt(2) * speed, Position(200, 200), MoveVector(Direction.Right, speed)).get
    //    interceptionVector shouldBe MoveVector(Direction.UpRight, speed)
    interceptionVector.moveVector.direction.toDegrees.toInt shouldBe 45 +- 1
  }
  it should """calculate interception no vector if can't intercept missile
    //...........M
    //...........|
    //...........|
    //...........|
    //..T """ in {
    val optionalInterceptionVector = calculateInterception(Position(0, 0), speed, Position(10000, 200), MoveVector(Direction.Down, speed))
    optionalInterceptionVector shouldBe None
  }
}
