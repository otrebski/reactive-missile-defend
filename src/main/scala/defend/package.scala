import defend.game.GameEngine.Protocol.RocketFired
import defend.model._

package object defend {

  val collisionDistance: Double = 8

  def move(position: Position, moveVector: MoveVector, millis: Long): Position = {
    Position(
      position.x + (moveVector.speed * Math.cos(moveVector.direction) * millis) / 1000,
      position.y + (moveVector.speed * Math.sin(moveVector.direction) * millis) / 1000
    )
  }

  def move(position: Position, moveVector: MoveVector, millis: Double): Position = {
    Position(
      position.x + (moveVector.speed * Math.cos(moveVector.direction) * millis) / 1000,
      position.y + (moveVector.speed * Math.sin(moveVector.direction) * millis) / 1000
    )
  }

  def findMissileToIntercept(list: List[WeaponInAction[AlienWeapon]], landScape: LandScape, tower: Position, speed: Double, range: Double): Option[WeaponInAction[AlienWeapon]] = {
    list.flatMap { w =>
      val height = w.position.y - landScape.groundLevel
      val dropSpeed = w.moveVector.speed * Math.sin(w.moveVector.direction)
      val hitTime = if (dropSpeed == 0) {
        Integer.MAX_VALUE
      } else {
        -(height / dropSpeed)
      }
      val weaponTypeFactor = w.weapon match {
        case _: AlienEmp  => 1
        case _: AlienNuke => 2
        case _: Any       => 3
      }
      val interceptionData: Option[InterceptionData] = calculateInterception(tower, speed, w.position, w.moveVector)
      interceptionData.toList.filter { i =>
        i.distance < range
      }.map(i => (i.duration * hitTime * weaponTypeFactor, i, w))
    }.sortBy(t => t._1).map(t => t._3).headOption
  }

  def findFirstToIntercept(list: List[WeaponInAction[AlienWeapon]], landScape: LandScape): Option[WeaponInAction[AlienWeapon]] = {
    list.sortBy { w =>
      val height = w.position.y - landScape.groundLevel
      val dropSpeed = w.moveVector.speed * Math.sin(w.moveVector.direction)
      if (dropSpeed == 0) {
        Integer.MAX_VALUE
      } else {
        -(height / dropSpeed)
      }
    }.headOption
  }

  def closeEnough(position1: Position, position2: Position, maxDistance: Double): Boolean = {
    val a: Double = position1.x - position2.x
    val b: Double = position1.y - position2.y
    a * a + b * b < maxDistance * maxDistance
    //    (Math.abs(a) < maxDistance) && (Math.abs(b) < maxDistance)
  }

  def findCollisions(human: List[WeaponInAction[HumanWeapon]], aliens: List[WeaponInAction[AlienWeapon]]): List[(WeaponInAction[HumanWeapon], List[WeaponInAction[AlienWeapon]])] = {
    human.foldLeft(List.empty[(WeaponInAction[HumanWeapon], List[WeaponInAction[AlienWeapon]])]) {
      (acc, item) =>
        val filter: List[WeaponInAction[AlienWeapon]] = aliens.filter(p => {
          closeEnough(item.position, p.position, collisionDistance)
        })
        if (filter.nonEmpty) {
          (item, filter) :: acc
        } else {
          acc
        }
    }
  }

  def damageCities(cities: List[City], explosions: List[Explosion]): List[City] = {
    cities.map {
      city =>
        val affectedBy: List[Explosion] = explosions.filter(e => closeEnough(city.position, e.position, e.weapon.explosionRadius))
        val sumDamage: Int = affectedBy.map(_.weapon.damage).sum

        city.copy(condition = Math.max(city.condition - sumDamage, 0))
    }
  }

  def experienceToLevel(exp: Int): Int = {
    Integer.toBinaryString(exp).length
  }

  def calculateDirection(start: Position, end: Position): MoveVector = {
    val x = end.x - start.x
    val y = end.y - start.y
    val z = Math.sqrt(x * x + y * y)

    val α = if (y > 0 && x > 0) {
      Math.atan(y / x)
    } else if (y > 0 && x < 0) {
      Math.PI + Math.atan(y / x)
    } else if (y < 0 && x < 0) {
      Math.PI + Math.atan(y / x)
    } else if (y < 0 && x > 0) {
      2 * Math.PI + Math.atan(y / x)
    } else if (x == 0) {
      if (y > 0) Math.PI / 2 else Math.PI * 1.5
    } else if (y == 0) {
      if (x > 0) 0 else Math.PI
    } else {
      0
    }

    MoveVector(α, z)
  }

  def distance(p1: Position, p2: Position): Double = {
    Math.sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y))
  }

  def calculateInterception(start: Position, chaserSpeed: Double, target: Position, targetVector: MoveVector): Option[InterceptionData] = {
    def dot(p1: Position, p2: Position): Double = {
      p1.x * p2.x + p1.y * p2.y
    }

    def quadraticSolver(a: Double, b: Double, c: Double): List[Double] = {
      val delta = b * b - 4 * a * c
      if (a == 0) {
        List(-c / b)
      } else if (delta < 0) {
        Nil
      } else {
        val deltaSqrt: Double = Math.sqrt(delta)
        val x1 = (-b + deltaSqrt) / (2 * a)
        val x2 = (-b - deltaSqrt) / (2 * a)
        List(x1, x2)
      }
    }

    val distanceToRunner = distance(start, target)
    val vectorFromRunner = Position(start.x - target.x, start.y - target.y)
    val vectorRunner = Position(targetVector.speed * Math.cos(targetVector.direction), targetVector.speed * Math.sin(targetVector.direction))
    //    val cosTheta = dot(vectorFromRunner, vectorRunner) / (distanceToRunner * targetVector.speed)
    val a = targetVector.speed * targetVector.speed - chaserSpeed * chaserSpeed
    val b = -2 * dot(vectorFromRunner, vectorRunner)
    val c = distanceToRunner * distanceToRunner

    val solutions: List[Double] = quadraticSolver(a, b, c)
    val interceptTimeMs = solutions.filter(_ >= 0).sorted.headOption
    interceptTimeMs.map {
      t =>
        val interceptionDuration: Double = t * 1000
        val hitPosition: Position = move(target, targetVector, interceptionDuration)
        val angle = calculateDirection(start, hitPosition).direction
        val moveVector = MoveVector(angle, chaserSpeed)
        InterceptionData(moveVector, hitPosition, interceptionDuration, distance(hitPosition, start))

    }.filter(r => r.moveVector.direction > 5d.toRadians && r.moveVector.direction < Math.PI - 5d.toRadians)

  }

  def reduceByLevel(value: Double, level: Int, reduction: Double): Double = {
    value * Math.pow(1 - reduction, level)
  }

  def angleErrorForLevel(level: Int): Double = {
    val nextLevelReduction = 0.20
    reduceByLevel(40d.toRadians, level - 1, nextLevelReduction)
  }

  def rangeForLevel(level: Int): Double = {
    (100 + level * (20 + level * 2)).toDouble
  }

  def fireMissile(me: DefenceTower, target: WeaponInAction[AlienWeapon], speed: Double, range: Double, explosionRadius: Double): Option[RocketFired] = {
    val vector: Option[InterceptionData] = defend.calculateInterception(me.position, speed, target.position, target.moveVector)
    vector.map {
      v =>
        RocketFired(
          HumanMissile(me.name, 1, explosionRadius),
          v.moveVector,
          me,
          Some(range * Math.sin(v.moveVector.direction) + me.position.y)
        )
    }
  }
}
