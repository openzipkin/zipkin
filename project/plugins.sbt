
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

resolvers ++= Seq(
  "travisci-central" at "http://maven.travis-ci.org/nexus/content/repositories/central/",
  "travisci-sonatype" at "http://maven.travis-ci.org/nexus/content/repositories/sonatype/",
  "twitter.com" at "http://maven.twttr.com/",
  "maven" at "http://repo1.maven.org/maven2/",
  "freemarker" at "http://freemarker.sourceforge.net/maven2/",
  "local" at ("file:" + System.getProperty("user.home") + "/.m2/repository/"))


libraryDependencies ++= Seq(
    "com.google.collections" % "google-collections" % "0.8",
    "org.codehaus.plexus"    % "plexus-utils"       % "1.5.4",
    "org.slf4j"              % "slf4j-api"          % "1.6.1",
    "org.slf4j"              % "slf4j-simple"       % "1.6.1",
    "com.twitter"           %% "scrooge-generator"  % "3.3.2")

