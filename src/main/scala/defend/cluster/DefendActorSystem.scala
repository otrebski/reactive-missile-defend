package defend.cluster

import akka.actor.{ Props, ActorSystem }
import akka.cluster.Cluster
import com.typesafe.config.{ Config, ConfigFactory }

import pl.project13.scala.rainbow.Rainbow._
import scala.concurrent.duration._
trait DefendActorSystem {
  println("Creating DefendActorSystem".green)
  //  private val clazz: Class[_] = this.getClass.getClassLoader.loadClass("defend.shard.FsmProtocol$AddExperience")
  println("Class loaded".green)

  val config: Config = ConfigFactory.load()
  val system: ActorSystem = ActorSystem("defend", config)
  system.actorOf(Props[SharedJournalSetter])

  Cluster(system).registerOnMemberRemoved {
    // exit JVM when ActorSystem has been terminated
    system.registerOnTermination(System.exit(-1))
    // in case ActorSystem shutdown takes longer than 10 seconds,
    // exit the JVM forcefully anyway
    system.scheduler.scheduleOnce(10.seconds)(System.exit(-1))(system.dispatcher)
    // shut down ActorSystem
    system.terminate()
  }

}
