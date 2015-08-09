package defend.game

import defend._
import defend.model._

import scala.util.Random

class StandardRandomWaveGenerator(quietPeriod: Long = 5000) extends WaveGenerator {

  val missilesPerWave = 10
  val maxSpeed = 100
  var lastWaveGeneratedTs = 0l

  override def nextWave(started: Long, currentTime: Long, landScape: LandScape, cities: List[City], defence: List[DefenceTower]): Option[Wave] = {
    if (currentTime - lastWaveGeneratedTs > quietPeriod) {
      lastWaveGeneratedTs = currentTime
      val weapons = (0 until missilesPerWave).map {
        i =>
          val start = Position(Random.nextInt(landScape.width), landScape.height)
          val end = Position(Random.nextInt(landScape.width), landScape.groundLevel)
          val duration = currentTime - started
          val missile: AlienWeapon = randomEnemyMissile(Random.nextInt(20))
          val baseSpeed: Double = 30 + Math.abs(Math.sin((duration / 300d).toRadians))
          var moveVector: MoveVector = calculateDirection(start, end).copy(speed = baseSpeed * speedRadio(missile))
          import scala.math._
          moveVector = moveVector.copy(max(250d.toRadians, min(moveVector.direction, 290d.toRadians)))
          val explodingVelocity = missile match {
            case a: AlienEmp => Some(landScape.groundLevel + 50d)
            case _           => None
          }
          val weaponInAction = WeaponInAction(missile, start, moveVector, explodingVelocity)
          weaponInAction
      }
      Some(Wave("A", weapons.toList))
    } else {
      None
    }
  }
}
