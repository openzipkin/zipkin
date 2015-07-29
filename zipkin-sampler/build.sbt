defaultSettings

libraryDependencies ++= Seq(
  many(finagle, "core", "httpx"),
  many(util, "core", "zk")
).flatten ++ testDependencies
