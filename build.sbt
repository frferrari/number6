name := "number6"

version := "1.0"

scalaVersion := "2.13.4"

lazy val akkaVersion = "2.6.10"
lazy val akkaHttpVersion = "10.2.2"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")

logBuffered in Test := false

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scalatest" %% "scalatest" % "3.1.4" % Test,
  "net.ruippeixotog" %% "scala-scraper" % "2.2.0",
  "org.typelevel" %% "cats-core" % "2.1.1" withSources()
)
