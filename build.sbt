lazy val missleDefend = project in file(".")

enablePlugins(JavaAppPackaging)

enablePlugins(LinuxPlugin)

enablePlugins(LauncherJarPlugin)

name := "missleDefend"

//fat jar

mainClass in assembly := some("defend.Boot")

mainClass in Compile := Some(" defend.Boot")

assemblyJarName := "reactive_missle_defend.jar"

//fat jar -end

Common.settings

libraryDependencies ++= Dependencies.akkaShard

initialCommands := """|import akka.actor._
                     |import akka.actor.ActorDSL._
                     |import akka.cluster._
                     |import akka.cluster.routing._
                     |import akka.routing._
                     |import akka.util._
                     |import com.typesafe.config._
                     |import scala.concurrent._
                     |import scala.concurrent.duration._""".stripMargin

addCommandAlias("cc", "runMain defend.DefenceCommandCenter ")

addCommandAlias("ui", "runMain defend.ui.UiApp")

addCommandAlias("st", "runMain defend.shardtest.ShardTest")

addCommandAlias("ui-test", "runMain defend.ui.JWarTheater")

addCommandAlias("sj", "runMain defend.cluster.SharedJournalApp  -Dakka.cluster.roles.0=shared-journal")

addCommandAlias("cliui", "runMain defend.ui.CliUi  -Dakka.cluster.roles.0=cliui")

