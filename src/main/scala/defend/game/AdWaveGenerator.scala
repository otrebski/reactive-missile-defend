package defend.game

import defend.model._

import scala.util.Random

class AdWaveGenerator(quietPeriod: Long = 6000) extends WaveGenerator {

  val maxSpeed = 100
  var lastWaveGeneratedTs = 0l
  val texts = List("ocado", "jdd", "akka", "scala", "kill node")

  override def nextWave(started: Long, currentTime: Long, landScape: LandScape, cities: List[City], defence: List[DefenceTower]): Option[Wave] = {
    {
      if (currentTime - lastWaveGeneratedTs > quietPeriod) {
        lastWaveGeneratedTs = currentTime

        val pos: List[Position] = Wave.stringPos(texts(Random.nextInt(texts.length)), landScape)
        val horizontalShift = Random.nextDouble() * (0d :: pos.map(_.x)).max
        val positions = pos.map(p => p.copy(x = p.x + horizontalShift))
        val baseSpeed: Double = 30 + Random.nextInt(30)
        val moveVector: MoveVector = MoveVector(Direction.Down, baseSpeed)
        val weapons = positions.map {
          p =>
            val missile: AlienWeapon = AlienMissile(1, 16 + Random.nextInt(16))
            val explodingVelocity = None
            val weaponInAction = WeaponInAction(missile, p, moveVector, explodingVelocity)
            weaponInAction
        }
        Some(Wave("A", weapons.toList))
      } else {
        None
      }
    }

  }
}
