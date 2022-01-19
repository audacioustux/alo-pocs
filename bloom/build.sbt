val scala3Version = "3.1.0"
val AkkaVersion = "2.6.18"

lazy val root = project
  .in(file("."))
  .settings(
    name := "bloom",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    Compile / mainClass := Some("HelloWorldMain"),
    Compile / run / fork := true,
    nativeImageGraalHome := file(sys.env("GRAALVM_HOME")).toPath,
    nativeImageOptions += s"-H:ReflectionConfigurationFiles=${target.value / "native-image-configs" / "reflect-config.json"}",
    nativeImageOptions += s"-H:ConfigurationFileDirectories=${target.value / "native-image-configs"}",
    nativeImageOptions += "-H:+JNI",
    nativeImageOptions ++=
      Seq(
        "--no-fallback",
        "--no-server",
        // "--language:wasm",
        "--language:js"
      ),
    javaOptions ++= Seq(
      "-Xmx16G"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test
    ).map(_.cross(CrossVersion.for3Use2_13)),
    libraryDependencies ++= Seq(
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "ch.qos.logback" % "logback-classic" % "1.3.0-alpha12"
    )
  )
  .enablePlugins(NativeImagePlugin)
