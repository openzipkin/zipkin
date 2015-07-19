defaultSettings

libraryDependencies ++= Seq(twitterServer) ++ many(finagle, "zipkin", "stats")
