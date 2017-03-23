name := "TinkoffBot"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions += "-feature"
scalacOptions += "-language:postfixOps"

libraryDependencies ++= {
  val akkaVersion = "2.4.17"
  Seq(
    "com.typesafe.akka" %% "akka-actor"           % akkaVersion,
    "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j"           % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core"       % "10.0.4",
    "com.typesafe.akka" %% "akka-http"            % "10.0.4",
    "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.4"
  )
}