defaultSettings

libraryDependencies ++= Seq(
  many(finagle, "thriftmux", "zipkin"),
  many(util, "app", "core"),
  scalaTestDeps
).flatten
