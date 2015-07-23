defaultSettings

parallelExecution in Test := false

libraryDependencies ++= Seq(
  "org.mongodb" %% "casbah" % "2.8.1",
  slf4jLog4j12,
  util("logging")
) ++ testDependencies
