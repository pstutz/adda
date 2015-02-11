addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.4.0")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

// Plugins below needed for build and code analysis - START

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("org.scala-sbt.plugins" % "sbt-onejar" % "0.8")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.0.1")

addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.0.0")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.0.0.BETA1")

// Plugins above needed for build and code analysis - END
