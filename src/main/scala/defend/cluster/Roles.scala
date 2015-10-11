package defend.cluster

object Roles {
  val Tower = "CommandCenter"
  trait SharedJournalRole {
    System.setProperty("akka.cluster.roles.0", "shared-journal")
  }
  trait CommandCenterRole {
    System.setProperty("akka.cluster.roles.0", Tower)
  }

}
