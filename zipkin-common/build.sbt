defaultSettings

libraryDependencies ++= Seq(
  Seq(util("core"), zk("client"), algebird("core"), ostrich),
  many(finagle, "ostrich4", "thrift", "zipkin", "exception"),
  scalaTestDeps
).flatten

dependencyOverrides ++= Set(
  "org.apache.zookeeper" % "zookeeper" % "3.4.6", // internal twitter + kafka + curator
  "org.slf4j" % "slf4j-api" % "1.6.4" // libthrift 0.5 otherwise pins 1.5.x
)
