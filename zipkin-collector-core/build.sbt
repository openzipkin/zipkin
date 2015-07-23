defaultSettings

libraryDependencies ++= Seq(
  Seq(algebird("core"), twitterServer, ostrich),
  many(finagle, "ostrich4", "serversets", "thrift", "zipkin"),
  many(util, "core", "zk", "zk-common"),
  many(zk, "candidate", "group")
).flatten ++ testDependencies
