import sbt._

object Version {

  val akka = "2.5.14"
//  val akkaDataReplication = "0.6"
  val logback = "1.1.3"
  val scala = "2.12.20"
  val scalaParsers = "1.0.5"
  val scalaTest = "3.0.8"
  val swing = "2.1.1"
  val otrosSocket = "1.0"
  val scalaLogging = "2.1.2"
}

object Library {
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % Version.akka
  val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % Version.akka
  val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % Version.akka
  val akkaClusterTools ="com.typesafe.akka" %% "akka-cluster-tools" % Version.akka
  val akkaContrib = "com.typesafe.akka" %% "akka-contrib" % Version.akka
  val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % Version.akka
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Version.akka
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Version.akka
  val scalaLogging ="com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
  val scalaSwing = "org.scala-lang.modules" % "scala-swing_2.12" % Version.swing
  val logbackClassic = "ch.qos.logback" % "logback-classic" % Version.logback

  val scalaParsers = "org.scala-lang.modules" %% "scala-parser-combinators" % Version.scalaParsers
  val scalaTest = "org.scalatest" %% "scalatest" % Version.scalaTest
//  val configFicus = "net.ceedubs" %% "ficus" % "1.1.2"
//  val levelDbjni = "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8"
  val leveldb = "org.iq80.leveldb"            % "leveldb"          % "0.12"
  val inmmem = "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2"
  val cassandra =  "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.98"
  val kryo ="com.github.romix.akka" %% "akka-kryo-serialization" % "0.5.1"
}

object Dependencies {

  import Library._

  val missileDefend = List(
    akkaCluster,
    akkaClusterTools,
    akkaClusterSharding,
    akkaContrib,
    akkaPersistence,
    scalaSwing,
    scalaLogging,
    akkaSlf4j,
    logbackClassic,
    scalaParsers,
//    configFicus,
    akkaTestkit % "test",
    scalaTest % "test",
//    levelDbjni % "test",
    leveldb ,
    inmmem,
    cassandra,
    kryo
  )
}
