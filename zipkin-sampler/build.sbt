defaultSettings

libraryDependencies ++= Seq(
  many(finagle, "core", "http"),
  many(util, "core", "zk")
).flatten ++ testDependencies
