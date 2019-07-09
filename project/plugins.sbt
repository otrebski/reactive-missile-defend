addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")

//addSbtPlugin("com.typesafe.training" % "sbt-koan" % "2.4.0")

// Actually IDE specific settings belong into ~/.sbt/,
// but in order to ease the setup for the training, we put the following here:

//addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.5.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.0")

//create fat jar http://stackoverflow.com/questions/28459333/how-to-build-an-uber-jar-fat-jar-using-sbt-within-intellij-idea

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")


addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.24")


