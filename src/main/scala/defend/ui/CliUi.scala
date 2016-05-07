package defend.ui

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor._
import akka.cluster.Cluster
import akka.event.Logging.MDC
import defend.cluster.{ DefendActorSystem, StatusKeeperProxy, StatusKeeperSingleton }
import defend.model._
import pl.project13.scala.rainbow

import scala.concurrent.duration._
import scala.language.postfixOps

object CliUi extends App
    with DefendActorSystem
    with StatusKeeperSingleton
    with StatusKeeperProxy {

  //  system.actorOf(Props[SharedJournalSetter])
  //  private lazy val settings: ClusterSingletonProxySettings =
  //    ClusterSingletonProxySettings(system).withSingletonName("statusKeeper")
  //  lazy val statusKeeper = system.actorOf(
  //    ClusterSingletonProxy.props(
  //      singletonManagerPath = "/user/singleton/",
  //      settings             = settings
  //    ),
  //    name = "statusKeeperProxy"
  //  )

  system.actorOf(Props(new CliUiActor(statusKeeperProxy)))

  class CliUiActor(statusKeeper: ActorRef) extends Actor with DiagnosticActorLogging {

    implicit val ec = scala.concurrent.ExecutionContext.global

    override def mdc(currentMessage: Any): MDC = {
      Map("node" -> Cluster(context.system).selfAddress.toString)
    }

    @throws[Exception](classOf[Exception])
    override def preStart(): Unit = {
      context.system.scheduler.scheduleOnce(1 second, statusKeeper, StatusKeeper.Protocol.UpdateRequest)
    }

    override def receive: Receive = {
      case w: WarTheater =>
        println()
        println()
        println("\u001b[2J")
        println(warTheaterToString(w))
        context.system.scheduler.scheduleOnce(1 second, statusKeeper, StatusKeeper.Protocol.UpdateRequest)
    }
  }

  def warTheaterToString(w: WarTheater): String = {
    import rainbow.Rainbow._
    val date = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date())
    val cities: String = w.city.filter(_.condition > 0).map { c =>
      val state = s"${c.condition}% ${"X" * (c.condition / 10)}"
      s"${c.name}:${if (c.condition < 50) state.red else state.toString.green}"
    }.mkString("\n")
    val towersReloading = w.defence.count(_.defenceTowerState == DefenceTowerReloading)
    val towersReady = w.defence.count(_.defenceTowerState == DefenceTowerReady)
    val towersInfected = w.defence.count(_.defenceTowerState == DefenceTowerInfected)

    val towersOffline: Int = w.defence.count(d => !d.isUp)
    val towers =
      s"Ready:     $towersReady: ${"|" * towersReady}".black.onGreen + "\n" +
        s"Reloading: $towersReloading: ${"|" * towersReloading}".white.onBlack + "\n" +
        s"Infected:  $towersInfected: ${"|" * towersInfected}".black.onYellow + "\n" +
        s"Offline:   $towersOffline: ${"|" * towersOffline}".red.onBlack

    val cc = w.commandCentres.map { c =>
      val status = c.status match {
        case CommandCenterOnline      => "Up         ".green
        case CommandCenterOffline     => "Down       ".red
        case CommandCenterUnreachable => "Unreachable".yellow
      }
      val towersCount: Int = w.defence.count(t => t.commandCenterName.contains(c.name))
      s"${c.name}[$status] => $towersCount"
    }.mkString("\n")

    s"$date\nCities:\n$cities\n\nDefence towers\n$towers\n\n Command centres:\n$cc\n\n" +
      s"Alien missiles: ${w.alienWeapons.size}\nHuman weapons: ${w.humanWeapons.size}"
  }
}

