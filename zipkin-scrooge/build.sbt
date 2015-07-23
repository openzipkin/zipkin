scroogeThriftSourceFolder in Compile := file("./zipkin-thrift/src/main/thrift")

libraryDependencies ++= Seq(
  Seq(util("core"), algebird("core"), ostrich),
  many(finagle, "ostrich4", "thrift", "zipkin"),
  many(scroogeDep, "core", "serializer")
).flatten ++ testDependencies
