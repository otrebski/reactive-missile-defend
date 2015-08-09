package defend.game

import defend.model._

import scala.util.Random

class RainWaveGenerator(quietPeriod: Long = 350) extends WaveGenerator {
  val missilesPerWave = 1
  val maxSpeed = 60
  var lastWaveGeneratedTs = 0l

  override def nextWave(started: Long, currentTime: Long, landScape: LandScape, cities: List[City], defence: List[DefenceTower]): Option[Wave] = {
    if (currentTime - lastWaveGeneratedTs > quietPeriod) {
      lastWaveGeneratedTs = currentTime
      val weapons = (0 until missilesPerWave).map {
        i =>
          val duration = currentTime - started

          val start = Position((duration / 5) % landScape.width, landScape.height)
          val baseSpeed: Double = 30 + Random.nextInt((maxSpeed * (1 - duration / (40 * 1000 + duration))).toInt)
          val missile: AlienWeapon = randomEnemyMissile(Random.nextInt(20))
          val moveVector: MoveVector = MoveVector(direction = (268 + Random.nextInt(5)).toDouble.toRadians, speed = baseSpeed * speedRadio(missile))
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
