defaultSettings

libraryDependencies ++= Seq(
  twitterServer,
  "org.apache.kafka" %% "kafka" % "0.8.1.1",
  "org.apache.commons" % "commons-io" % "1.3.2",
  scroogeDep("serializer")
) ++ scalaTestDeps
