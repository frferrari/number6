name := "number6"

version := "1.0"

scalaVersion := "2.13.4"

lazy val AkkaVersion = "2.6.10"
lazy val akkaHttpVersion = "10.2.2"
lazy val SlickVersion = "3.3.2"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")

logBuffered in Test := false

// Generate scala classes for all protos under "google/type"
// PB.protoSources in Compile += PB.externalIncludePath.value / "google" / "type"

// Since protoSources is passed to the include path, and externalIncludePath is already in there
// we need to remove this extra path to prevent duplicate compilation.
// PB.includePaths in Compile -= PB.externalIncludePath.value / "google" / "type"

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value / "scalapb"
)

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
  "com.typesafe.slick" %% "slick" % SlickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,

  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.10" % "1.17.0-0" % "protobuf",
  "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.10" % "1.17.0-0",
  "com.google.api.grpc" % "googleapis-common-protos" % "0.0.3" % "protobuf-src" intransitive(),


  "org.postgresql" % "postgresql" % "42.2.5",

  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scalatest" %% "scalatest" % "3.1.4" % Test,
  "net.ruippeixotog" %% "scala-scraper" % "2.2.0",
  "org.typelevel" %% "cats-core" % "2.1.1" withSources()
)

val GrpcProtosArtifact = "com.google.api.grpc" % "grpc-google-common-protos" % "1.17.0"

lazy val googleCommonProtos = (project in file("google-common-protos"))
  .settings(
    name := "google-common-protos",

    // Dependencies marked with "protobuf" get extracted to target / protobuf_external
    libraryDependencies ++= Seq(
      GrpcProtosArtifact % "protobuf"
    ),

    // In addition to the JAR we care about, the protobuf_external directory
    // is going to contain protos from Google's standard protos.
    // In order to avoid compiling things we don't use, we restrict what's
    // compiled to a subdirectory of protobuf_external
    PB.protoSources in Compile += target.value / "protobuf_external" / "google" / "type",

    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    )
  )

// This sub-project is where your code goes. It contains proto file that imports a proto
// from the external proto jar.
lazy val myProject = (project in file("number6"))
  .settings(
    name := "number6",

    // The protos in this sub-project depend on the protobufs in
    // GrpcProtosArtifact, so we need to have them extracted here too. This
    // time we do not add them to `PB.protoSources` so they do not compile.
    libraryDependencies ++= Seq(
      GrpcProtosArtifact % "protobuf"
    ),

    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    ),

  )
  .dependsOn(googleCommonProtos)
