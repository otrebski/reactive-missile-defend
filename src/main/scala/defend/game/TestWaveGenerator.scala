package defend.game

import defend._
import defend.model._

import scala.util.Random

class TestWaveGenerator(quietPeriod: Long = 20000) extends WaveGenerator {

  val waveWidth = 4
  val maxSpeed = 100
  var lastWaveGeneratedTs = 0l

  override def nextWave(started: Long, currentTime: Long, landScape: LandScape, cities: List[City], defence: List[DefenceTower]): Option[Wave] = {
    {
      if (currentTime - lastWaveGeneratedTs > quietPeriod) {
        lastWaveGeneratedTs = currentTime
        val spaceBetween = 10 + Random.nextInt(10)
        val start = Position(Random.nextInt(landScape.width - spaceBetween * waveWidth), landScape.height)
        val end = start.copy(y = landScape.groundLevel)

        val baseSpeed: Double = 30 + Random.nextInt(30)
        val moveVector: MoveVector = calculateDirection(start, end).copy(speed = baseSpeed)
        val weapons = (0 until waveWidth).map {
          i =>
            val missile: AlienWeapon = AlienMissile(1, 16 + Random.nextInt(16))
            val explodingVelocity = None
            val weaponInAction = WeaponInAction(missile, start.copy(x = start.x + i * spaceBetween), moveVector, explodingVelocity)
            weaponInAction
        }

        Some(Wave("A", weapons.toList))
      } else {
        None
      }
    }

  }
}
