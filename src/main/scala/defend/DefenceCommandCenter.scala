package defend

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{ ClusterDomainEvent, InitialStateAsEvents }
import defend.cluster._
import pl.project13.scala.rainbow.Rainbow._

import scala.annotation.tailrec
import scala.io.StdIn

object DefenceCommandCenter extends App
    with SharedLevelDb
    with DefendActorSystem
    with StatusKeeperSingleton
    with StatusKeeperProxy
    with TowerShard
    with Terminal {

  system.actorOf(Props[SharedJournalSetter])

  private val clusterMonitor: ActorRef = system.actorOf(Props(new ClusterMonitor))
  private val cluster: Cluster = Cluster(system)
  cluster.subscribe(clusterMonitor, InitialStateAsEvents, classOf[ClusterDomainEvent])
  system.eventStream.subscribe(clusterMonitor, classOf[DeadLetter])
  println(" Defence started".green)
  println(""" Type "shutdown" to stop [call system.shutdown()]""".black.onYellow)
  println(""" Type "leave" to leave cluster [call cluster.leave(cluster.selfAddress)] """.black.onYellow)

  commandLoop()

  @tailrec
  private def commandLoop(): Unit = {
    val parser: CommandParser.Parser[Command] = CommandParser.shutdown | CommandParser.leave
    Command(StdIn.readLine(), parser) match {
      case Command.Shutdown =>
        println("Shutting down".green)
        system.terminate()
      case Command.Leave =>
        println("Leaving cluster".green)
        cluster.leave(cluster.selfAddress)
      case Command.Unknown(command, message) =>
        println(s"Unknown command $command".red)
        commandLoop()
    }

  }
}
