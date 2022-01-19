val scala3Version = "3.0.1"
val AkkaVersion = "2.6.16"

lazy val root = project
  .in(file("."))
  .enablePlugins(JmhPlugin)
  .enablePlugins(NativeImagePlugin)
  .settings(
    name := "fromscratch1",
    version := "0.1.0",
    scalaVersion := scala3Version,
    Compile / run / fork := true,
    javaOptions ++= Seq(
      "-Xmx16G",
      "-agentlib:native-image-agent=config-output-dir=/Users/tanjimhossain/Bytes/poc-wormhole/fromscratch1/config-out"
    ),
    Compile / mainClass := Some("bench.CtxPool.CtxPoolMain"),
    nativeImageGraalHome := file(sys.env("GRAALVM_HOME")).toPath,
    nativeImageOptions += s"-H:ReflectionConfigurationFiles=${target.value / "native-image-configs" / "reflect-config.json"}",
    nativeImageOptions += s"-H:ConfigurationFileDirectories=${target.value / "native-image-configs"}",
    nativeImageOptions += "-H:+JNI",
    nativeImageOptions ++=
      Seq(
        "--no-fallback",
        "--no-server",
        "--language:wasm"
      ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "io.jvm.uuid" %% "scala-uuid" % "0.3.1"
    ).map(_.cross(CrossVersion.for3Use2_13)),
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.3.0-alpha12"
    )
  )
