defaultSettings

libraryDependencies ++= Seq(
  Seq(finagle("core")),
  many(util, "core", "zk"),
  many(zk, "candidate", "group")
).flatten ++ testDependencies
