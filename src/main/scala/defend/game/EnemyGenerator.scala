package defend.game

import akka.actor.{ Actor, Props }
import defend.game.EnemyGenerator.PingForEnemies
import defend.model._

class EnemyGenerator(started: Long, timeProvider: () => Long, waveGenerator: WaveGenerator) extends Actor {

  override def receive: Receive = {
    case s: PingForEnemies =>
      val wave: Option[Wave] = waveGenerator.nextWave(started, timeProvider(), s.landScape, s.city, s.defence)
      wave.foreach(sender() ! _)
  }
}

object EnemyGenerator {

  def props(started: Long, timeProvider: () => Long = System.currentTimeMillis, waveGenerator: WaveGenerator): Props = {
    Props(new EnemyGenerator(started, timeProvider, waveGenerator))
  }

  case class PingForEnemies(
    defence:      List[DefenceTower]                = List.empty,
    city:         List[City]                        = List.empty,
    alienWeapons: List[WeaponInAction[AlienWeapon]] = List.empty,
    humanWeapons: List[WeaponInAction[HumanWeapon]] = List.empty,
    landScape:    LandScape
  )

}
