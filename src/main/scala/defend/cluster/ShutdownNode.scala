package defend.cluster

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.sharding.ShardRegion
import pl.project13.scala.rainbow._

import scala.concurrent.{ Await, Awaitable, CanAwait }
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

trait ShutdownNode {

  val system: ActorSystem

  private val shutdown: String = "shutdown"

  system.actorOf(Props(new ShutdownActor), shutdown)
  val towerShard: ActorRef

  class ShutdownActor extends Actor {
    override def receive: Receive = {
      case ShutdownNode.Terminate =>
        println("                                         ".onRed)
        println("   Have received command to terminate    ".onWhite.red)
        println("                                         ".onRed)
        system.terminate()

      case ShutdownNode.LeaveCluster =>
        println("                                             ".onRed)
        println("   Have received command to leave cluster    ".onWhite.red)
        println("   Trying to do graceful leave               ".onWhite.red)
        println("                                             ".onRed)
        val cluster: Cluster = Cluster(system)
        cluster.leave(cluster.selfAddress)
        val awaitable = new Awaitable[Boolean] {
          override def ready(atMost: Duration)(implicit permit: CanAwait): this.type = this

          override def result(atMost: Duration)(implicit permit: CanAwait): Boolean = cluster.isTerminated
        }
        Await.ready(awaitable, 30 seconds)
      

      case Terminated(`towerShard`) ⇒
        println("                                             ".onRed)
        println("   Cluster region was terminated             ".onWhite.red)
        println("   Terminate and leave                       ".onWhite.red)
        println("                                             ".onRed)
        system.registerOnTermination(System.exit(0))
        system.terminate()

      case ShutdownNode.SystemExit0 =>
        println("                                              ".onRed)
        println("   Have received command to System.exit(0)    ".onWhite.red)
        println("                                              ".onRed)
        System.exit(0)

      case _: Any =>
    }

  }

}

object ShutdownNode {

  case object LeaveCluster

  case object Terminate

  case object SystemExit0

}
