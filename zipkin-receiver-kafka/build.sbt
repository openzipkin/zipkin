defaultSettings

libraryDependencies ++= Seq(
  twitterServer,
  "org.apache.kafka" %% "kafka" % "0.8.2.1",
  "org.apache.commons" % "commons-io" % "1.3.2",
  "com.github.charithe" % "kafka-junit" % "1.4" % "test",
  scroogeDep("serializer")
) ++ scalaTestDeps

dependencyOverrides ++= Set(
  // Twitter's build is pinned to 4.10, but we need 4.11+ to use rules in Scala.
  // 4.11 allows @Rule to be declared on a method, which works around the fact
  // that Scala cannot generate fields with public visibility.
  "junit" % "junit" % "4.11"
)
