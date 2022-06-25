import Dependencies._

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "zio",
        scalaVersion := "2.13.8",
        version := "0.1.3-SNAPSHOT"
      )
    ),
    name := "zio-entity"
  )
  .settings(noPublishSettings)

lazy val noPublishSettings = Seq(publish := (()), publishLocal := (()), publishArtifact := false)

val testDeps = Seq(
  "org.scalatest" %% "scalatest" % "3.2.11" % Test,
  "dev.zio" %% "zio-test-sbt" % zio % Test,
  "dev.zio" %% "zio-test-magnolia" % zio % Test
)

val allDeps = Seq(
  "dev.zio" %% "zio" % zio,
  "dev.zio" %% "zio-streams" % zio,
  "dev.zio" %% "zio-test" % zio,
  "io.suzaku" %% "boopickle" % "1.4.0",
  "org.scala-lang" % "scala-reflect" % "2.13.8",
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion
) ++ testDeps

val exampleDeps = Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
) ++ testDeps

val postgresDeps = Seq(
  "org.tpolecat" %% "doobie-core" % "1.0.0-RC2",
  "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC2",
  "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC2",
  "dev.zio" %% "zio-interop-cats" % "3.3.0",
  "ch.qos.logback" % "logback-classic" % "1.2.11" % Test,
  "org.testcontainers" % "postgresql" % "1.17.2" % Test
) ++ testDeps

val akkaDeps = Seq(
  "com.typesafe.akka" %% "akka-cluster-sharding" % "2.6.19",
  "com.typesafe.akka" %% "akka-cluster" % "2.6.19",
) ++ testDeps


lazy val commonProtobufSettings = Seq(
  Compile / PB.targets := Seq(
    scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
//    scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value / "scalapb"
),
  Compile / PB.protoSources := Seq(
    baseDirectory.value / "src/schemas/protobuf"
  )
)

def module(id: String, path: String, description: String): Project =
  Project(id, file(path))
    .settings(moduleName := id, name := description)
    .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val `core` = module("zio-entity-core", "core", "Core library")
  .settings(libraryDependencies ++= allDeps)

lazy val `postgres` = module("zio-entity-postgres", "postgres", "Postgres event sourcing stores")
  .dependsOn(`core`)
  .settings(libraryDependencies ++= postgresDeps)
  .settings(commonProtobufSettings)

lazy val `akka-runtime` = module("zio-entity-akkaruntime", "akka-runtime", "Akka runtime")
  .dependsOn(`core`)
  .settings(libraryDependencies ++= akkaDeps)
  .settings(commonProtobufSettings)

lazy val `benchmarks` = module("benchmarks", "benchmarks", "Benchmarks")
  .dependsOn(`core`, `akka-runtime`, `postgres`)
  .settings(noPublishSettings)

lazy val `example` = module("example", "example", "Example of credit card processing")
  .dependsOn(`core`, `akka-runtime`, `postgres`)
  .settings(libraryDependencies ++= exampleDeps)
  .settings(commonProtobufSettings)
  .settings(noPublishSettings)

lazy val docs = project       // new documentation project
  .in(file("zio-entity-docs")) // important: it must not be docs/
  .dependsOn(`core`, `akka-runtime`, `postgres`)
  .enablePlugins(MdocPlugin)

aggregateProjects(`core`, `akka-runtime`, `postgres`, `benchmarks`, `example`)


import ReleaseTransformations._
releaseIgnoreUntrackedFiles := true
releasePublishArtifactsAction := PgpKeys.publishSigned.value

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runClean,                               // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
  setNextVersion,                         // : ReleaseStep
  commitNextVersion,                      // : ReleaseStep
)

  ThisBuild / parallelExecution := false
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")