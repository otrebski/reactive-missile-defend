package defend.cluster

import akka.actor.{ Actor, ActorSystem, Props }
import akka.cluster.Cluster

trait ShutdownNode {

  val system: ActorSystem

  private val shutdown: String = "shutdown"

  system.actorOf(Props(new ShutdownActor), shutdown)

  class ShutdownActor extends Actor {
    override def receive: Receive = {
      case ShutdownNode.Terminate =>
        import pl.project13.scala.rainbow.Rainbow._
        println("                                         ".onRed)
        println("   Have received command to terminate    ".onWhite.red)
        println("                                         ".onRed)
        system.terminate()

      case ShutdownNode.LeaveCluster =>
        import pl.project13.scala.rainbow.Rainbow._
        println("                                             ".onRed)
        println("   Have received command to leave cluster    ".onWhite.red)
        println("                                             ".onRed)
        val cluster: Cluster = Cluster(system)
        cluster.leave(cluster.selfAddress)

      case ShutdownNode.DownNode =>
        import pl.project13.scala.rainbow.Rainbow._
        println("                                             ".onRed)
        println("   Have received command to down node        ".onWhite.red)
        println("                                             ".onRed)
        val cluster: Cluster = Cluster(system)
        cluster.down(cluster.selfAddress)

      case ShutdownNode.SystemExit0 =>
        import pl.project13.scala.rainbow.Rainbow._
        println("                                              ".onRed)
        println("   Have received command to System.exit(0)    ".onWhite.red)
        println("                                              ".onRed)
        System.exit(0)

    }

  }

}

object ShutdownNode {

  case object LeaveCluster

  case object DownNode

  case object Terminate

  case object SystemExit0

}
