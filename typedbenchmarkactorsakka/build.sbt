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
    
    javaOptions ++= Seq("-XX:+CMSClassUnloadingEnabled", "-XX:MaxPermSize=20G", "-Xmx20G", "-Xms20G"),
    // javaHome := Some(file("/Library/Java/JavaVirtualMachines/graalvm-ee-java11-21.1.0/Contents/Home")),
    javaHome := Some(file("/Users/tanjimhossain/Downloads/graal/graalvm-ee-java11-21.1.0/Contents/Home")),
    // run / javaHome := Some(file("/Users/tanjimhossain/Downloads/graal/graalvm-ee-java11-21.1.0/Contents/Home")),

    libraryDependencies += ("com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion).cross(CrossVersion.for3Use2_13),
    libraryDependencies += ("com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test).cross(CrossVersion.for3Use2_13),
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",

  )

