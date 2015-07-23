defaultSettings

libraryDependencies ++= Seq(
  finagle("thriftmux"),
  util("zk"),
  slf4jLog4j12
) ++ testDependencies
