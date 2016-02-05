package defend.model

import defend.PersistenceMonitor.{ PersistenceState, PersistenceUnknown }
import defend.ui.StatusKeeper.Protocol.LostMessages

class Model

trait Weapon {
  def damage: Int

  def explosionRadius: Double
}

//Enemy weapon
sealed trait AlienWeapon extends Weapon

case class AlienMissile(override val damage: Int, override val explosionRadius: Double) extends AlienWeapon

case class AlienBomb(override val damage: Int, override val explosionRadius: Double) extends AlienWeapon

case class AlienNuke(override val damage: Int, override val explosionRadius: Double) extends AlienWeapon

case class AlienEmp(override val damage: Int, override val explosionRadius: Double) extends AlienWeapon

//cartography
case class MoveVector(direction: Double, speed: Double)

case class Position(x: Double, y: Double)

case class Explosion(position: Position, weapon: Weapon)

case class ExplosionEvent(explosion: Explosion, expire: Float)

//earth structure
case class City(name: String, position: Position, condition: Int)

case class DefenceTower(name: String, position: Position)

case class DefenceTowerStatus(
  defenceTower:         DefenceTower,
  defenceTowerState:    DefenceTowerState,
  isUp:                 Boolean,
  commandCenterName:    Option[String]    = None,
  level:                Int               = 0,
  lastMessageTimestamp: Option[Long]      = None
)

sealed trait DefenceTowerState

case object DefenceTowerReady extends DefenceTowerState

case object DefenceTowerReloading extends DefenceTowerState

case object DefenceTowerInfected extends DefenceTowerState

//Earth weapons
trait HumanWeapon extends Weapon {
  def towerName: String
}

case class HumanMissile(override val towerName: String, override val damage: Int, override val explosionRadius: Double) extends HumanWeapon

case class LandScape(width: Int, height: Int, groundLevel: Int)

case class WeaponInAction[+T](weapon: T, position: Position, moveVector: MoveVector, explodeVelocity: Option[Double] = None)

case class InterceptionData(moveVector: MoveVector, hitPoint: Position, duration: Double, distance: Double)

//Command centres
case class CommandCenter(name: String, status: CommandCenterStatus, lastMessageTimestamp: Long = 0)

sealed trait CommandCenterStatus

case object CommandCenterOnline extends CommandCenterStatus

case object CommandCenterOffline extends CommandCenterStatus

case object CommandCenterUnreachable extends CommandCenterStatus

//War theater
case class WarTheater(
  defence:          List[DefenceTowerStatus]          = List.empty,
  city:             List[City]                        = List.empty,
  alienWeapons:     List[WeaponInAction[AlienWeapon]] = List.empty,
  humanWeapons:     List[WeaponInAction[HumanWeapon]] = List.empty,
  landScape:        LandScape,
  commandCentres:   List[CommandCenter]               = List.empty,
  explosions:       List[ExplosionEvent]              = List.empty,
  points:           Integer                           = 0,
  timestamp:        Long                              = System.currentTimeMillis(),
  clusterLeader:    Option[String]                    = None,
  persistenceState: PersistenceState                  = PersistenceUnknown(),
  lostMessages:     List[LostMessages]                = List.empty[LostMessages]

)

case class OptionalWarTheater(
  defence:        Option[List[DefenceTower]]                = None,
  cities:         Option[List[City]]                        = None,
  alienWeapons:   Option[List[WeaponInAction[AlienWeapon]]] = None,
  humanWeapons:   Option[List[WeaponInAction[HumanWeapon]]] = None,
  commandCentres: Option[List[CommandCenter]]               = None,
  explosions:     Option[List[Explosion]]                   = None,
  points:         Option[Integer]                           = None,
  landScape:      Option[LandScape]                         = None,
  clusterLeader:  Option[String]                            = None
)

case object Direction {
  val Left = Math.PI
  val Up = Math.PI / 2
  val Down = 270d.toRadians
  val DownLeft = 5 * Math.PI / 4
  val DownRight = 7 * Math.PI / 4
  val UpLeft = 3 * Math.PI / 4
  val UpRight = 1 * Math.PI / 4
  val Right = 0d
}