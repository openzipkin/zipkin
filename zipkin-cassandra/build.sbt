defaultSettings

libraryDependencies ++= Seq(
  "commons-codec" % "commons-codec" % "1.6",
  finagle("serversets"),
  scroogeDep("serializer"),
  "org.iq80.snappy" % "snappy" % "0.1",
  "org.mockito" % "mockito-all" % "1.9.5" % "test",
  "com.twitter" %% "scalding-core" % "0.11.2",
  hadoop("client")
) ++ many(util, "logging", "app") ++ testDependencies ++ scalaTestDeps

addConfigsToResourcePathForConfigSpec()

