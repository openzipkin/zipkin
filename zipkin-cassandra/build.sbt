defaultSettings

libraryDependencies ++= Seq(
  "commons-codec" % "commons-codec" % "1.6",
  finagle("serversets"),
  scroogeDep("serializer"),
  "org.iq80.snappy" % "snappy" % "0.3",
  "com.twitter" %% "scalding-core" % "0.15.0",
  hadoop("client")
) ++ many(util, "logging", "app") ++ testDependencies

addConfigsToResourcePathForConfigSpec()

resolvers += "Conjars" at "http://conjars.org/repo"
