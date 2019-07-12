import com.typesafe.sbt.SbtScalariform._
import sbt.Keys._
import sbt._

import scalariform.formatter.preferences._

object Common {


  val settings =
    List(
      // Core settings
      organization := "otrebski",
      version := "1.0.2",
      scalaVersion := Version.scala,
      crossScalaVersions := List(scalaVersion.value),
      resolvers ++= List(
        "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven",
        "Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
        "Otros at bintray" at "https://bintray.com/artifact/download/otros-systems/maven"
      ),
      scalacOptions ++= List(
        "-unchecked",
        "-deprecation",
        "-language:_",
        "-target:jvm-1.8",
        "-encoding", "UTF-8"
      ),
      unmanagedSourceDirectories in Compile := List((scalaSource in Compile).value),
      unmanagedSourceDirectories in Test := List((scalaSource in Test).value),
      // Scalariform settings
      ScalariformKeys.preferences := ScalariformKeys.preferences.value
        .setPreference(AlignArguments, true)
        .setPreference(AlignParameters, true)
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
        .setPreference(DoubleIndentMethodDeclaration, true)
    )
}
