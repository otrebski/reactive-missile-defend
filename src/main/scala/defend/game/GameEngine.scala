package defend.game

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterSharding
import akka.event.Logging.MDC
import defend._
import defend.game.GameEngine.Protocol.{ AlienRocketFired, RocketFired, Tick }
import defend.model._
import defend.shard.TowerActor
import defend.shard.TowerActor.Protocol.MessageOfDeath

import scala.concurrent.duration._
import scala.language.{ implicitConversions, postfixOps }

class GameEngine(var defence: List[DefenceTower], var city: List[City], landScape: LandScape, waveGenerator: WaveGenerator, statusKeeper: ActorRef) extends Actor with DiagnosticActorLogging {

  private var lastTimestamp: Long = System.currentTimeMillis()
  private var alienWeaponsInAction = List.empty[WeaponInAction[AlienWeapon]]
  private var humanWeaponInAction = List.empty[WeaponInAction[HumanWeapon]]
  private var allExplosions = List.empty[Explosion]
  private var rocketsFired = List.empty[RocketFired]
  private var points = 0
  private var waves = List.empty[Wave]
  private val waveGeneratorActor = context.actorOf(EnemyGenerator.props(System.currentTimeMillis(), System.currentTimeMillis, waveGenerator))

  implicit val ec = context.system.dispatcher
  private val towerShard: ActorRef = ClusterSharding(context.system).shardRegion(TowerActor.shardRegion)
  private val towerPings: List[Cancellable] = defence.map(d => context.system.scheduler.schedule(10 millis, 100 millis, towerShard, Envelope(d.name, TowerActor.Protocol.Ping)))
  private val selfAddress: Address = Cluster(context.system).selfAddress
  private var index = 0

  override def mdc(currentMessage: Any): MDC = {
    Map("node" -> selfAddress)
  }

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(20 millis, self, Tick)
    log.debug("Starting game engine")
  }

  override def receive: Receive = {
    case rf: RocketFired =>
      rocketsFired = rf :: rocketsFired
    case arf: AlienRocketFired =>
      alienWeaponsInAction = WeaponInAction(arf.alienWeapon, arf.start, arf.moveVector, arf.explodingVelocity) :: alienWeaponsInAction
    case wave: Wave =>
      waves = wave :: waves
    case Tick =>
      //process tick
      waveGeneratorActor ! EnemyGenerator.PingForEnemies(defence, city, alienWeaponsInAction, humanWeaponInAction, landScape)
      val now = System.currentTimeMillis()
      val timeDiff: Long = now - lastTimestamp
      log.debug(s"Processing tick, time diff=$timeDiff")
      alienWeaponsInAction = alienWeaponsInAction ::: waves.flatMap(w => w.weaponsInAction)
      waves = List.empty[Wave]
      humanWeaponInAction = humanWeaponInAction ::: rocketsFired.map(rf => WeaponInAction(rf.humanWeapon, rf.defenceTower.position, rf.moveVector, rf.explodingVelocity))
      rocketsFired = List.empty
      humanWeaponInAction = humanWeaponInAction.filter(w => w.position.y < landScape.height && w.position.y > 0)

      val hits: List[(WeaponInAction[HumanWeapon], List[WeaponInAction[AlienWeapon]])] = findCollisions(humanWeaponInAction, alienWeaponsInAction)
      val (withGround: List[WeaponInAction[AlienWeapon]], alienAboveGround) = alienWeaponsInAction.partition(w => w.position.y <= w.explodeVelocity.getOrElse(landScape.groundLevel + 20d))
      alienWeaponsInAction = alienAboveGround

      val explosionsWithGround: List[Explosion] = withGround.map(x => Explosion(x.position, x.weapon))
      //explosion after reaching velocity
      val (humanExplodingAtVelocity, humanNotExplodingAtVelocity) = humanWeaponInAction.partition(h => h.explodeVelocity.exists(ev => h.position.y > ev))
      humanWeaponInAction = humanNotExplodingAtVelocity

      val (humanAffectedByExplosion, humanNotAffectedByExplosions) = humanWeaponInAction.partition(h => allExplosions.exists(e => closeEnough(e.position, h.position, e.weapon.explosionRadius)))
      humanWeaponInAction = humanNotAffectedByExplosions

      val (alienAffectedByExplosion, alienNotAffectedByExplosions) = alienWeaponsInAction.partition(h => allExplosions.exists(e => closeEnough(e.position, h.position, e.weapon.explosionRadius)))
      alienWeaponsInAction = alienNotAffectedByExplosions

      val hitsSet: Set[WeaponInAction[HumanWeapon]] = hits.map(e => e._1).toSet
      humanWeaponInAction = humanWeaponInAction.filterNot(e => hitsSet.contains(e))
      val alienHit = hits.flatMap(e => e._2).toSet
      alienWeaponsInAction = alienWeaponsInAction.filterNot(e => alienHit.contains(e))

      val hitExplosions: List[Explosion] = hits.flatMap { hit => weaponInActionToExplosion(hit._1) :: hit._2.map(weaponInActionToExplosion(_)) }

      allExplosions = explosionsWithGround :::
        hitExplosions :::
        humanExplodingAtVelocity.map(w => Explosion(w.position, w.weapon)) :::
        humanAffectedByExplosion.map(w => Explosion(w.position, w.weapon)) :::
        alienAffectedByExplosion.map(w => Explosion(w.position, w.weapon))

      city = city.map {
        c =>
          val affectedBy: List[Explosion] = allExplosions.filter(e => closeEnough(c.position, e.position, e.weapon.explosionRadius))
          val damageToCity: Int = affectedBy.map(e => e.weapon.damage).sum
          c.copy(condition = Math.max(c.condition - damageToCity, 0))
      }

      lastTimestamp = System.currentTimeMillis()
      alienWeaponsInAction = alienWeaponsInAction.map(w => w.copy(position = move(w.position, w.moveVector, timeDiff)))
      humanWeaponInAction = humanWeaponInAction.map(w => w.copy(position = move(w.position, w.moveVector, timeDiff)))

      val xx: List[WeaponInAction[AlienWeapon]] = hits.flatMap(a => a._2).distinct
      val pointsInc: List[Int] = xx.map(w => w.weapon).map(w => w.damage)
      val sum1: Int = pointsInc.sum
      points = points + sum1

      val optionalWarTheater: OptionalWarTheater = OptionalWarTheater(
        defence      = Some(defence),
        cities       = Some(city),
        alienWeapons = Some(alienWeaponsInAction),
        humanWeapons = Some(humanWeaponInAction),
        explosions   = Some(allExplosions),
        points       = Some(points),
        landScape    = Some(landScape)
      )
      statusKeeper ! optionalWarTheater

      lastTimestamp = now

      index = index+1
      defence.foreach { t =>
        log.debug(s"Notify tower $t")
        val envelope: Envelope = Envelope(t.name, TowerActor.Protocol.Situation(index,t, alienWeaponsInAction, landScape))
        towerShard ! envelope
      }

      //Add experience to tower for directs hits
      hits.map(a => (a._1.weapon.towerName, a._2.map(b => b.weapon.damage).sum)).
        foreach(a => {
          val envelope: Envelope = Envelope(a._1, TowerActor.Protocol.ExperienceGained(a._2))
          towerShard ! envelope
        })

      //restart towers by EMP hit
      allExplosions.filter { e =>
        e.weapon match {
          case emp: AlienEmp => true
          case _             => false
        }
      }.foreach { explosion =>
        defence.foreach { t =>
          if (defend.closeEnough(explosion.position, t.position, explosion.weapon.explosionRadius)) {
            val envelope: Envelope = Envelope(t.name, MessageOfDeath(alienEmp = explosion.weapon.asInstanceOf[AlienEmp]))
            towerShard ! envelope
          }
        }
      }

      //check game condition
      val sum: Int = city.map(_.condition).sum
      if (sum > 0) {
        implicit val ec = context.system.dispatcher
        context.system.scheduler.scheduleOnce(50 millis, self, Tick)
      } else {
        defence.foreach(t => towerShard ! Envelope(t.name, PoisonPill))
      }
  }

  implicit def weaponInActionToExplosion(weaponInAction: WeaponInAction[Weapon]): Explosion = {
    Explosion(weaponInAction.position, weaponInAction.weapon)
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    super.postStop()
    defence.foreach { t =>
      towerShard ! Envelope(t.name, PoisonPill)
    }
    towerPings.foreach(_.cancel())
  }
}

object GameEngine {

  def props(
    defence:       List[DefenceTower],
    city:          List[City],
    landScape:     LandScape,
    waveGenerator: WaveGenerator,
    statusKeeper:  ActorRef
  ) = {
    Props(classOf[GameEngine], defence, city, landScape, waveGenerator, statusKeeper)
  }

  case object Protocol {

    case object Tick

    case class RocketFired(humanWeapon: HumanWeapon, moveVector: MoveVector, defenceTower: DefenceTower, explodingVelocity: Option[Double])

    case class AlienRocketFired(alienWeapon: AlienWeapon, start: Position, moveVector: MoveVector, explodingVelocity: Option[Double])

  }

}