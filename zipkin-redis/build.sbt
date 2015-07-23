defaultSettings

parallelExecution in Test := false

libraryDependencies ++= Seq(
  finagle("redis"),
  util("logging"),
  scroogeDep("serializer"),
  slf4jLog4j12
) ++ testDependencies

addConfigsToResourcePathForConfigSpec()
