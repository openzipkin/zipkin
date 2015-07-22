name := "my-collector"

organization := "com.twitter"

scalaVersion := "2.10.5"

libraryDependencies ++= Seq(
  "com.twitter" % "zipkin-cassandra" % "1.2.0-SNAPSHOT",
  "com.twitter" % "zipkin-collector" % "1.2.0-SNAPSHOT",
  "com.twitter" % "zipkin-receiver-scribe" % "1.2.0-SNAPSHOT",
  "com.twitter" %% "twitter-server" % "1.11.0")

lazy val resolvers = Seq(
  Resolver.jcenterRepo,
  "Twitter Maven Repository" at "http://maven.twttr.com/"
)
