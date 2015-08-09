package defend.cluster

import akka.actor.{ ActorSystem, Props }
import com.typesafe.config.{ Config, ConfigFactory }

object SharedJournalTestClient extends App with SharedLevelDb {

  import pl.project13.scala.rainbow.Rainbow._

  val config: Config = ConfigFactory.load()
  val system: ActorSystem = ActorSystem("defend", config)
  system.actorOf(Props[SharedJournalSetter])
  println("Shared journal test client started".green)
}
