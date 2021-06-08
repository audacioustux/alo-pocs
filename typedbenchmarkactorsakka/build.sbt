val scala3Version = "3.0.0"
val scala2Version = "2.13.6"
val AkkaVersion = "2.6.14"

lazy val root = project
  .in(file("."))
  .enablePlugins(JmhPlugin)
  .settings(
    name := "scala3-simple",
    version := "0.1.0",
    scalaVersion := scala3Version,
    trapExit := false,

    libraryDependencies += "com.github.florianingerl.util" % "regex" % "1.1.9",
    // libraryDependencies += "org.graalvm.sdk" % "graal-sdk" % "21.1.0",
    libraryDependencies += ("com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion).cross(CrossVersion.for3Use2_13),
    libraryDependencies += ("com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test).cross(CrossVersion.for3Use2_13),
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",

    Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated
  )
