package defend

import akka.actor.{ Actor, ActorRef, PoisonPill, Props }
import akka.pattern.ask
import akka.persistence._
import akka.util.Timeout
import defend.PersistenceMonitor._
import pl.project13.scala.rainbow._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.language.postfixOps
import scala.util.Success

class PersistenceMonitor(sendStateTo: ActorRef, timeProvider: () => Long) extends Actor {

  implicit val timeout: Timeout = Timeout(2 seconds)
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    context.system.scheduler.schedule(2 seconds, 4 seconds, self, Ping)
  }

  override def receive: Receive = {
    case Ping =>
      val persistenceTest: String = "persistenceTest" + System.currentTimeMillis()
      val props: Props = Props(new TestingPersistentActor(persistenceTest))
      val actor: ActorRef = context.actorOf(props)
      val string: String = System.currentTimeMillis().toString
      val future: Future[Any] = actor ? Save(string)
      actor ! SaveSnapshot
      future.onComplete {
        case Success(Saved) =>
          actor ! PoisonPill
          val actor2 = context.actorOf(props)
          val futureState = actor2 ? GetState
          futureState.onComplete {
            case Success(Loaded(`string`)) =>
              sendStateTo ! PersistenceOk(timeProvider())
            case Success(_) =>
              sendStateTo ! PersistenceError(timeProvider())
            case _ =>
              sendStateTo ! PersistenceError(timeProvider())
          }
        case _: Any =>
          sendStateTo ! PersistenceError(timeProvider())
      }

  }
}

object PersistenceMonitor {

  case object Ping

  case class Save(status: String)

  case object Saved

  case object GetState

  case class Loaded(status: String)

  case object SaveSnapshot

  trait PersistenceState {
    val timestamp: Long
  }

  case class PersistenceOk(timestamp: Long) extends PersistenceState

  case class PersistenceError(timestamp: Long) extends PersistenceState

  case class PersistenceUnknown() extends PersistenceState {
    override val timestamp: Long = 0
  }

}

class TestingPersistentActor(id: String) extends PersistentActor {

  var status = "?"

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, Save(s)) => status = s
    case RecoveryCompleted         =>
    case Save(s)                   => status = s
    case a: Any                    => println(s"Recover Unknown message: $a".yellow)
  }

  override def receiveCommand: Receive = {
    case Save(s) =>
      persist(Save(s))(x => status = x.status)
      sender() ! Saved
    case GetState =>
      sender() ! Loaded(status)
    case SaveSnapshot =>
      saveSnapshot(Save(status))
    case SaveSnapshotSuccess(_)        => println(s"Successfully saved snapshot".green)
    case SaveSnapshotFailure(_, cause) => println(s"Failure saving snapshot $cause".red)
    case a: Any                        => println(s"Received unknown message $a".yellow)

  }

  override def persistenceId: String = id
}
