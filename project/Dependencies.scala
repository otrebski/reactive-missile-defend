import sbt._

object Version {

  val akka = "2.3.8"
  val akkaDataReplication = "0.6"
  val logback = "1.1.3"
  val scala = "2.11.7"
  val scalaParsers = "1.0.2"
  val scalaTest = "2.2.2"
  val swing = "1.0.0"
  val otrosSocket = "1.0"
  val scalaLogging = "2.1.2"
}

object Library {
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % Version.akka
  val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % Version.akka
  val akkaContrib = "com.typesafe.akka" %% "akka-contrib" % Version.akka
  val akkaPersistence = "com.typesafe.akka" %% "akka-persistence-experimental" % Version.akka
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Version.akka
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Version.akka
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging-slf4j" % Version.scalaLogging
  val scalaSwing = "org.scala-lang.modules" % "scala-swing_2.11.0-RC1" % Version.swing
  val logbackClassic = "ch.qos.logback" % "logback-classic" % Version.logback
  val otrosSocket = "pl.otros.logback.socket" % "OtrosLogbackSocketAppender" % Version.otrosSocket
//  val slf4j = "org.slf4j" % "slf4j-simple" % "1.7.12"

  val scalaParsers = "org.scala-lang.modules" %% "scala-parser-combinators" % Version.scalaParsers
  val scalaTest = "org.scalatest" %% "scalatest" % Version.scalaTest
//  val persitanceJdbc = "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.1.5"
  val h2database = "com.h2database" % "h2" % "1.4.187"
  val scalaRainbow = "pl.project13.scala" %% "rainbow" % "0.2"
  val configFicus = "net.ceedubs" %% "ficus" % "1.1.2"
  val levelDbjni = "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8"
  val leveldb = "org.iq80.leveldb"            % "leveldb"          % "0.7"
  val inmmem = "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.0.3"
}

object Dependencies {

  import Library._

  val missileDefend = List(
    akkaCluster,
    akkaContrib,
    akkaPersistence,
    akkaSlf4j,
    scalaSwing,
    scalaLogging,
    logbackClassic,
    otrosSocket,
//    slf4j,
    scalaParsers,
//    persitanceJdbc,
    h2database,
    scalaRainbow,
    configFicus,
    akkaTestkit % "test",
    scalaTest % "test",
    levelDbjni % "test",
  leveldb % "test",
  inmmem
  )
}
