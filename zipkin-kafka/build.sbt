defaultSettings

libraryDependencies ++= Seq(
  "com.twitter" %% "tormenta-kafka" % "0.10.0",
  scroogeDep("serializer")
) ++ testDependencies


// for storm-kafka
resolvers += "clojars" at "https://clojars.org/repo"
// com.twitter:kafka
resolvers += "conjars" at "http://conjars.org/repo"
