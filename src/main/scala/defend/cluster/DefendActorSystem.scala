package defend.cluster

import akka.actor.{ Props, ActorSystem }
import com.typesafe.config.{ Config, ConfigFactory }

import pl.project13.scala.rainbow.Rainbow._
trait DefendActorSystem {
  println("Creating DefendActorSystem".green)
  val config: Config = ConfigFactory.load()
  val system: ActorSystem = ActorSystem("defend", config)
  system.actorOf(Props[SharedJournalSetter])

}
