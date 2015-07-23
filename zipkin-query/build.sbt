defaultSettings

libraryDependencies ++= Seq(
  many(finagle, "thriftmux", "zipkin"),
  many(util, "app", "core")
).flatten ++ testDependencies
