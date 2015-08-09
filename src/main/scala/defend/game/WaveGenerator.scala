package defend.game

import defend.model._

import scala.util.Random

trait WaveGenerator {

  def nextWave(started: Long, currentTime: Long, landScape: LandScape, cities: List[City], defence: List[DefenceTower]): Option[Wave]

  def randomEnemyMissile(int: Int) = {
    val missile: AlienWeapon = {
      int match {
        case 0           => AlienNuke(20 + Random.nextInt(10), 50 + Random.nextInt(100))
        case i if i < 14 => AlienBomb(5 + Random.nextInt(5), 30 + Random.nextInt(20))
        case i if i < 19 => AlienMissile(1 + Random.nextInt(4), 20 + Random.nextInt(20))
        case _           => AlienEmp(1, 100)
      }
    }
    missile
  }

  def speedRadio(w: AlienWeapon): Double = {
    w match {
      case a: AlienMissile => 3
      case a: AlienBomb    => 1
      case a: AlienNuke    => 2
      case a: AlienEmp     => 3
    }
  }
}
