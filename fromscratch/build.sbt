val scala3Version = "3.0.0"
val AkkaVersion = "2.6.14"

lazy val root = project
  .in(file("."))
  .enablePlugins(JmhPlugin)
  .settings(
    name := "fromscratch",
    version := "0.1.0",
    scalaVersion := scala3Version,
    Compile / run / fork := true,
    // javaOptions ++= Seq("-Xmx16G", "-verbose:gc", "-XX:+UseG1GC"),
    // javaOptions ++= Seq("-Xmn16G", "-Xmx16G", "-Xms16G"),
    // javaHome := Some(file("/Library/Java/JavaVirtualMachines/graalvm-ee-java11-21.1.0/Contents/Home")),
    // javaHome := Some(file("/Library/Java/JavaVirtualMachines/graalvm-ee-java16-21.1.0/Contents/Home")),
    libraryDependencies ++= Seq(
      // "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
      // "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test
    ).map(_.cross(CrossVersion.for3Use2_13)),
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3"
      // "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"
    )
  )
