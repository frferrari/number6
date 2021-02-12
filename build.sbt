import sbt.Keys.{logBuffered, scalaVersion}

name := "priceScraper"
version := "1.0"
scalaVersion := "2.13.4"
showTiming := true
logBuffered in Test := false

val akkaVersion = "2.6.10"
val akkaHttpVersion = "10.2.1"
val akkaManagementVersion = "1.0.9"
val akkaPersistenceCassandraVersion = "1.0.4"
val alpakkaKafkaVersion = "2.0.5"
val akkaProjectionVersion = "1.0.0"

val slickVersion = "3.3.2"
val elastic4sVersion = "7.10.2"

val scalikeJdbcVersion = "3.5.0"

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
val akkaHttp2Support = "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion
val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
val akkaPersistenceCassandra = "com.typesafe.akka" %% "akka-persistence-cassandra" % akkaPersistenceCassandraVersion
val akkaPersistenceQuery = "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion
val akkaProjectionCore = "com.lightbend.akka" %% "akka-projection-core" % "1.1.0"
val akkaProjectionEventSourced = "com.lightbend.akka" %% "akka-projection-eventsourced" % akkaProjectionVersion
val akkaProjectionJdbc = "com.lightbend.akka" %% "akka-projection-jdbc" % akkaProjectionVersion
val akkaStreamKafka = "com.typesafe.akka" %% "akka-stream-kafka" % alpakkaKafkaVersion

val slick = "com.typesafe.slick" %% "slick" % slickVersion
val slickHirakiCp = "com.typesafe.slick" %% "slick-hikaricp" % slickVersion

val postgresql = "org.postgresql" % "postgresql" % "42.2.18"
val scalikeJdbc = "org.scalikejdbc" %% "scalikejdbc" % scalikeJdbcVersion
val scalikeJdbcConfig = "org.scalikejdbc" %% "scalikejdbc-config" % scalikeJdbcVersion
val reactiveMongo = "org.reactivemongo" %% "reactivemongo" % "1.0.2"

val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
val scalaTest = "org.scalatest" %% "scalatest" % "3.1.4" % Test
val scalaScraper = "net.ruippeixotog" %% "scala-scraper" % "2.2.0"
val catsCore = "org.typelevel" %% "cats-core" % "2.1.1" withSources()

enablePlugins(AkkaGrpcPlugin)

libraryDependencies ++= Seq(
  akkaActorTyped,
  akkaActorTestkitTyped,

  akkaClusterTyped,
  akkaClusterShardingTyped,

  // akkaManagement,
  // akkaManagementClusterHttp,
  // akkaManagementClusterBootstrap,

  akkaDiscovery,

  akkaPersistenceTyped,
  akkaPersistenceCassandra,
  akkaPersistenceQuery,
  akkaPersistenceTestkit,

  akkaSerializationJackson,

  akkaStream,

  akkaHttp2Support,
  akkaHttp,
  akkaHttpSprayJson,

  // akkaProjectionCore,
  // akkaProjectionEventSourced,
  // akkaProjectionJdbc,

  // slick,
  // slickHirakiCp,

  // postgresql,
  // reactiveMongo,
  // scalikeJdbc,
  // scalikeJdbcConfig,

  logbackClassic,

  scalaTest,
  scalaScraper,
  catsCore
)

// testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
