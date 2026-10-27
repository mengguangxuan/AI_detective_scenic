ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "detective"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file(".")).settings(
  name := "detective-minimal-fixture",
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % "3.5.4",
    "io.circe" %% "circe-generic" % "0.14.9",
    "io.circe" %% "circe-parser" % "0.14.9",
    "com.h2database" % "h2" % "2.3.232",
    "org.scalameta" %% "munit" % "1.0.2" % Test
  ),
  Compile / run / mainClass := Some("services.system.api.FixtureDemo"),
  Compile / run / fork := true,
  Compile / run / javaOptions ++= Seq("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"),
  Test / javaOptions ++= Seq("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"),
  Test / fork := true
)
