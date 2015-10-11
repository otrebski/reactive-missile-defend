package defend

import akka.actor.Actor
import akka.cluster.ClusterEvent._
import pl.project13.scala.rainbow.Rainbow._

class ClusterMonitor extends Actor {
  override def receive: Receive = {
    case m: MemberUp          => println(s"Member ${m.member.address} is up with roles ${m.member.roles}".green)
    case m: UnreachableMember => println(s"Member ${m.member.address} is unreachable  ${m.member.roles}".yellow)
    case m: ReachableMember   => println(s"Member ${m.member.address} is reachable  ${m.member.roles}".green)
    case m: MemberRemoved     => println(s"Member ${m.member.address} is removed  ${m.member.roles}".red)
    case m: MemberExited      => println(s"Member ${m.member.address} is exited  ${m.member.roles}".black.onRed)
  }
}
