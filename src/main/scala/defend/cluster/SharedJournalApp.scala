package defend.cluster

import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.typesafe.scalalogging.slf4j.LazyLogging
import defend.ClusterMonitor
import pl.project13.scala.rainbow.Rainbow._

object SharedJournalApp extends App
    with Roles.SharedJournalRole
    with SharedLevelDb
    with DefendActorSystem
    with StatusKeeperSingleton
    with StatusKeeperProxy
    with LazyLogging {

  logger.info("Starting Shared Journal APP")
  println("Setting roles property".green)
  println(s"Role property: ${System.getProperty("akka.cluster.roles.0")}".green)

  startJournal(system)

  //  system.actorOf(Props[SharedJournalSetter])

  val cm = system.actorOf(Props(new ClusterMonitor))
  Cluster(system).subscribe(cm, InitialStateAsEvents,
    classOf[MemberUp],
    classOf[ReachableMember],
    classOf[MemberExited],
    classOf[UnreachableMember])
}
