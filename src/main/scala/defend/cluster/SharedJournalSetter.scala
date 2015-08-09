package defend.cluster

import akka.actor.{ Actor, RootActorPath }
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{ InitialStateAsEvents, MemberUp }

class SharedJournalSetter extends Actor with SharedLevelDb {
  override def preStart(): Unit = Cluster(context.system).subscribe(self, InitialStateAsEvents, classOf[MemberUp])

  override def receive: Receive = {
    case MemberUp(m) if m.hasRole("shared-journal") =>
      val path = RootActorPath(m.address) / "user" / "store"
      setuppSharedJournalOnRemote(path, context.system)
  }
}
