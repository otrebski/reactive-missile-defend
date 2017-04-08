import sbt._

object Version {

  val akka = "2.4.17"
  val akkaDataReplication = "0.6"
  val logback = "1.1.3"
  val scala = "2.11.8"
  val scalaParsers = "1.0.5"
  val scalaTest = "3.0.1"
  val swing = "1.0.1"
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
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging-slf4j" % Version.scalaLogging
  val scalaSwing = "org.scala-lang.modules" % "scala-swing_2.11.0-RC4" % Version.swing
  val logbackClassic = "ch.qos.logback" % "logback-classic" % Version.logback

  val scalaParsers = "org.scala-lang.modules" %% "scala-parser-combinators" % Version.scalaParsers
  val scalaTest = "org.scalatest" %% "scalatest" % Version.scalaTest
  val scalaRainbow = "pl.project13.scala" %% "rainbow" % "0.2"
  val configFicus = "net.ceedubs" %% "ficus" % "1.1.2"
//  val levelDbjni = "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8"
  val leveldb = "org.iq80.leveldb"            % "leveldb"          % "0.7"
  val inmmem = "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.2.13"
  val cassandra =  "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.25"
  val kryo = "com.github.romix.akka" %% "akka-kryo-serialization" % "0.5.0"
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
    scalaRainbow,
    configFicus,
    akkaTestkit % "test",
    scalaTest % "test",
//    levelDbjni % "test",
    leveldb ,
    inmmem,
    cassandra,
    kryo
  )
}
