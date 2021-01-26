name := "number6"

version := "1.0"

scalaVersion := "2.13.4"

lazy val AkkaVersion = "2.6.10"
lazy val akkaHttpVersion = "10.2.2"
lazy val SlickVersion = "3.3.2"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")

logBuffered in Test := false

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

  "com.lightbend.akka" %% "akka-persistence-jdbc" % "4.0.0",
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "com.lightbend.akka" %% "akka-projection-core" % "1.1.0",
  "com.typesafe.slick" %% "slick" % SlickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,

  "org.postgresql" % "postgresql" % "42.2.5",

  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scalatest" %% "scalatest" % "3.1.4" % Test,
  "net.ruippeixotog" %% "scala-scraper" % "2.2.0",
  "org.typelevel" %% "cats-core" % "2.1.1" withSources()
)
