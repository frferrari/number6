name := "akka-quickstart-scala"

version := "1.0"

scalaVersion := "2.13.1"

scalacOptions += "-Ypartial-unification"

lazy val akkaVersion = "2.6.10"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "net.ruippeixotog" %% "scala-scraper" % "2.2.0",
  "org.typelevel" %% "cats-core" % "2.1.1"
)
