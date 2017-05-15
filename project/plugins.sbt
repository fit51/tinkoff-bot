resolvers += Classpaths.typesafeReleases

logLevel := Level.Warn

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.5")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.0")