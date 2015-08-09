package defend.game

import defend._
import defend.model._

import scala.util.Random

class IntelligentWaveGenerator(quietPeriod: Long = 4000) extends WaveGenerator {

  val maxWaveSize = 5
  val maxSpeed = 180
  var lastWaveGeneratedTs = 0l

  override def nextWave(started: Long, currentTime: Long, landScape: LandScape, cities: List[City], defence: List[DefenceTower]): Option[Wave] = {
    {
      if (currentTime - lastWaveGeneratedTs > quietPeriod) {
        lastWaveGeneratedTs = currentTime
        val waveWidth = Random.nextInt(maxWaveSize) + 1
        val waveHeight = Random.nextInt(5) + 1
        val spaceBetween = 10 + Random.nextInt(20)
        val cityPositions: List[Position] = cities.filter(_.condition > 0).map(_.position)
        val end = cityPositions.lift(Random.nextInt(cityPositions.size)).getOrElse(Position(0, landScape.groundLevel))
        val start = Position(end.x - 50 + Random.nextInt(100), landScape.height + waveHeight * 16)

        val duration = currentTime - started
        val baseSpeed: Double = 30 + 100 * Math.abs(Math.sin((duration / 300d).toRadians))
        val moveVector: MoveVector = calculateDirection(start, end).copy(speed = baseSpeed)
        val weapons = (0 until waveHeight).flatMap { row =>
          (0 until waveWidth).map {
            col =>
              val missile: AlienWeapon = AlienMissile(2 + Random.nextInt(2), 10)
              val explodingVelocity = None
              val position: Position = start.copy(x = start.x + col * spaceBetween, y = start.y - 16 * row)
              val weaponInAction = WeaponInAction(missile, position, moveVector, explodingVelocity)
              weaponInAction
          }
        }
        val nuke = WeaponInAction(AlienNuke(10 + Random.nextInt(40), 30 + Random.nextInt(50)), start.copy(x = start.x + 20, y = start.y + 40), moveVector)
        val emp = WeaponInAction(AlienEmp(10 + Random.nextInt(40), 130 + Random.nextInt(50)), start.copy(x = start.x + 60, y = start.y + 40), moveVector)

        val fastEmp = if (Random.nextInt(10) == 1)
          List(WeaponInAction(AlienEmp(10 + Random.nextInt(40), 130 + Random.nextInt(50)), start.copy(x = start.x + 60, y = start.y + 40), moveVector.copy(speed = baseSpeed * 2)))
        else Nil
        Some(Wave("A", nuke :: emp :: weapons.toList ::: fastEmp))
      } else {
        None
      }
    }

  }
}
