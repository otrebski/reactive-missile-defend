package defend

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{ ClusterDomainEvent, InitialStateAsEvents }
import defend.cluster._
import defend.ui.FileUi
import pl.project13.scala.rainbow._

object DefenceCommandCenter extends App
  with Roles.CommandCenterRole
  with DefendActorSystem
  //    with StatusKeeperSingleton
  with StatusKeeperProxy
  with TowerShard
  with ShutdownNode
  with Terminal {

  //  system.actorOf(Props[SharedJournalSetter])

  private val clusterMonitor: ActorRef = system.actorOf(Props(new ClusterMonitor))
  val cluster: Cluster = Cluster(system)
  cluster.subscribe(clusterMonitor, InitialStateAsEvents, classOf[ClusterDomainEvent])
  system.eventStream.subscribe(clusterMonitor, classOf[DeadLetter])

  if (config.getBoolean("rmd.fileUI.use")) {
    val file: String = config.getString("rmd.fileUI.file")
    println(s"Adding file ui with file $file".green)
    system.actorOf(FileUi.props(file))
  } else {
    println("Not adding File UI. To use export USE_FILE_UI=true and FILE_UI=status.txt".green)
  }
  println(" Defence started".green)
  println(""" Type "shutdown" to stop [call system.shutdown()]""".black.onYellow)
  println(""" Type "leave" to leave cluster [call cluster.leave(cluster.selfAddress)] """.black.onYellow)

}
