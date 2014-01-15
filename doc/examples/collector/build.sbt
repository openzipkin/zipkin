name := "my-collector"

organization := "com.twitter"

scalaVersion := "2.9.2"

libraryDependencies ++= Seq(
  "com.twitter" % "zipkin-cassandra" % "1.1.1-SNAPSHOT",
  "com.twitter" % "zipkin-collector" % "1.1.1-SNAPSHOT",
  "com.twitter" % "zipkin-receiver-scribe" % "1.1.1-SNAPSHOT")

resolvers ++= Seq(
  "twitter.com" at "http://maven.twttr.com/",
  "maven" at "http://repo1.maven.org/maven2/",
  "freemarker" at "http://freemarker.sourceforge.net/maven2/",
  "local" at ("file:" + System.getProperty("user.home") + "/.m2/repository/"))
