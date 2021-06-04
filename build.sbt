import Dependencies._

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "zio",
        scalaVersion := "2.13.6",
        version := "0.1.3-SNAPSHOT"
      )
    ),
    name := "zio-entity"
  )
  .settings(noPublishSettings)

lazy val noPublishSettings = Seq(publish := (()), publishLocal := (()), publishArtifact := false)

val testDeps = Seq(
  "org.scalatest" %% "scalatest" % "3.2.9" % Test,
  "dev.zio" %% "zio-test-sbt" % zio % Test,
  "dev.zio" %% "zio-test-magnolia" % zio % Test
)


val allDeps = Seq(
  "dev.zio" %% "zio" % zio,
  "dev.zio" %% "zio-streams" % zio,
  "dev.zio" %% "zio-test" % zio,
  "dev.zio" %% "zio-schema" % "0.0.5",
  "org.scala-lang" % "scala-reflect" % "2.13.6",
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion
) ++ testDeps

val postgresDeps = Seq(
  "org.tpolecat" %% "doobie-core" % "0.13.4",
  "org.tpolecat" %% "doobie-hikari" % "0.13.4",
  "org.tpolecat" %% "doobie-postgres" % "0.13.4",
  "dev.zio" %% "zio-interop-cats" % "2.5.1.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
  "org.testcontainers" % "postgresql" % "1.15.3" % Test
) ++ testDeps

val akkaDeps = Seq(
  "com.typesafe.akka" %% "akka-cluster-sharding" % "2.6.14",
  "com.typesafe.akka" %% "akka-cluster" % "2.6.14",
) ++ testDeps

val k8dnsDeps = Seq(
  "dev.zio" %% "zio-memberlist" % "0.0.0+6-97eb0ea1+20210601-0929-SNAPSHOT"
) ++ testDeps

lazy val commonProtobufSettings = Seq(
  Compile / PB.targets := Seq(
    scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
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

lazy val `k8dns-runtime` = module("zio-entity-k8dnsruntime", "k8dns-runtime", "k8 DNS runtime")
  .dependsOn(`core`)
  .settings(libraryDependencies ++= k8dnsDeps)
  .settings(commonProtobufSettings)

lazy val docs = project       // new documentation project
  .in(file("zio-entity-docs")) // important: it must not be docs/
  .dependsOn(`core`, `akka-runtime`, `postgres`)
  .enablePlugins(MdocPlugin)

aggregateProjects(`core`, `akka-runtime`, `k8dns-runtime`, `postgres`)

ThisBuild / parallelExecution := false
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")