import sbt.Keys.{logBuffered, scalaVersion}

name := "priceScraper"
version := "1.0"
scalaVersion := "2.13.4"
showTiming := true
logBuffered in Test := false

lazy val akkaVersion = "2.6.10"
lazy val akkaHttpVersion = "10.2.2"
lazy val akkaManagementVersion = "1.0.9"
lazy val slickVersion = "3.3.2"

val akkaActorTyped = "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
val akkaActorTestkitTyped = "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test
val akkaClusterTyped = "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion
val akkaClusterShardingTyped = "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion
val akkaManagement = "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion
val akkaManagementClusterHttp = "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion
val akkaManagementClusterBootstrap = "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion
val akkaDiscovery = "com.typesafe.akka" %% "akka-discovery" % akkaVersion

val akkaPersistenceTyped = "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion
val akkaPersistenceTestkit = "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion % Test
val akkaSerializationJackson = "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion
val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
val akkaPersistenceJdbc = "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.0.0"
val akkaPersistenceQuery = "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion
val akkaProjectionCore = "com.lightbend.akka" %% "akka-projection-core" % "1.1.0"
val akkaProjectionEventSourced = "com.lightbend.akka" %% "akka-projection-eventsourced" % "1.1.0"
val akkaProjectionJdbc = "com.lightbend.akka" %% "akka-projection-jdbc" % "1.1.0"
val akkaGrpc = ""
val slick = "com.typesafe.slick" %% "slick" % slickVersion
val slickHirakiCp = "com.typesafe.slick" %% "slick-hikaricp" % slickVersion
val postgresql = "org.postgresql" % "postgresql" % "42.2.5"
val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
val scalatest = "org.scalatest" %% "scalatest" % "3.1.4" % Test
val scalaScraper = "net.ruippeixotog" %% "scala-scraper" % "2.2.0"
val catsCore = "org.typelevel" %% "cats-core" % "2.1.1" withSources()

enablePlugins(AkkaGrpcPlugin)

libraryDependencies ++= Seq(
  akkaActorTyped,
  akkaActorTestkitTyped,

  akkaClusterTyped,
  akkaClusterShardingTyped,

  akkaManagement,
  akkaManagementClusterHttp,
  akkaManagementClusterBootstrap,

  akkaDiscovery,

  akkaPersistenceTyped,
  akkaPersistenceJdbc,
  akkaPersistenceQuery,
  akkaPersistenceTestkit,

  akkaSerializationJackson,

  akkaStream,
  akkaHttp,
  akkaHttpSprayJson,

  akkaProjectionCore,
  akkaProjectionEventSourced,
  akkaProjectionJdbc,

  slick,
  slickHirakiCp,

  postgresql,

  logbackClassic,

  scalatest,
  scalaScraper,
  catsCore
)

// testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
