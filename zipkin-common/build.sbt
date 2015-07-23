defaultSettings

libraryDependencies ++= Seq(
  Seq(util("core"), zk("client"), algebird("core"), ostrich),
  many(finagle, "ostrich4", "thrift", "zipkin", "exception")
).flatten ++ testDependencies
