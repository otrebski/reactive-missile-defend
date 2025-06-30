package defend.cluster

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.{ Config, ConfigFactory }
import pl.project13.scala.rainbow._

import scala.concurrent.duration._
trait DefendActorSystem {
  println("Creating DefendActorSystem".green)

  val config: Config = ConfigFactory.load()
  val system: ActorSystem = ActorSystem("defend", config)

  Cluster(system).registerOnMemberRemoved {
    // exit JVM when ActorSystem has been terminated
    system.registerOnTermination(System.exit(0))
    // in case ActorSystem shutdown takes longer than 10 seconds,
    // exit the JVM forcefully anyway
    system.scheduler.scheduleOnce(10.seconds)(System.exit(0))(system.dispatcher)
    // shut down ActorSystem
    system.terminate()
  }

}
