import com.twitter.scrooge.ScroogeSBT

defaultSettings ++ ScroogeSBT.newSettings

ScroogeSBT.scroogeThriftSourceFolder in Compile <<= (
  (baseDirectory in ThisBuild)
  (_ / "zipkin-thrift" / "src" / "main" / "thrift" / "com" / "twitter" / "zipkin" )
)

libraryDependencies ++= Seq(
  Seq(util("core"), algebird("core"), ostrich),
  many(finagle, "ostrich4", "thrift", "zipkin"),
  many(scroogeDep, "core", "serializer"),
  scalaTestDeps
).flatten
