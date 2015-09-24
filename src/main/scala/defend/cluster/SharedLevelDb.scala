package defend.cluster

import akka.actor._
import akka.pattern._
import akka.persistence.journal.leveldb.{ SharedLeveldbJournal, SharedLeveldbStore }
import akka.util.Timeout
import pl.project13.scala.rainbow.Rainbow._

import scala.concurrent.duration._

trait SharedLevelDb {

  def startJournal(system: ActorSystem): Unit = {
    println("Startup shared journal".green)
    val of: ActorRef = system.actorOf(Props[SharedLeveldbStore], "store")
    println(s"Started store actor $of".green)
  }

  def setupSharedJournalOnRemote(path: ActorPath, system: ActorSystem): Unit = {
    // Start the shared journal one one node (don't crash this SPOF)
    // This will not be needed with a distributed journal
    println("Startup shared journal".green)
    //    println(s"Shared journal path is $path".onGreen)
    // register the shared journal
    import system.dispatcher
    implicit val timeout = Timeout(15.seconds)
    val f = system.actorSelection(path) ? Identify(None)
    f.onSuccess {
      case ActorIdentity(_, Some(ref)) =>
        println(s"Have actor identify of store $ref".green)
        SharedLeveldbJournal.setStore(ref, system)
      case m: Any =>
        system.log.error("Shared journal not started at {}", path)
        println(s"Shared journal not started at $path".red)
        println(s"Received: $m")
        system.terminate()
    }
    f.onFailure {
      case _ =>
        println(s"Lookup of shared journal at $path timed out".red)
        system.terminate()
    }
  }
}