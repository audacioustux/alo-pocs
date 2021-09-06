val scala3Version = "3.0.1"
val AkkaVersion = "2.6.16"

lazy val root = project
  .in(file("."))
  .enablePlugins(JmhPlugin)
  .settings(
    name := "fromscratch1",
    version := "0.1.0",
    scalaVersion := scala3Version,
    Compile / run / fork := true,
    javaOptions ++= Seq("-Xmx16G"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test
    ).map(_.cross(CrossVersion.for3Use2_13)),
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    )
  )
