defaultSettings

libraryDependencies ++= Seq(
  "com.twitter" %% "tormenta-kafka" % "0.8.0",
  scroogeDep("serializer")
) ++ testDependencies

resolvers ++= resolversIfNoProxyRepo("clojars" at "http://clojars.org/repo")

