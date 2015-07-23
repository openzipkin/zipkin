resolvers += Resolver.url("bintray-sbt-plugins", url("http://dl.bintray.com/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

libraryDependencies ++= Seq(
    "com.google.collections" % "google-collections" % "0.8",
    "org.codehaus.plexus"    % "plexus-utils"       % "1.5.4",
    "org.slf4j"              % "slf4j-api"          % "1.6.1",
    "org.slf4j"              % "slf4j-simple"       % "1.6.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.8.1")

addSbtPlugin("com.twitter" %% "scrooge-sbt-plugin" % "3.16.3")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.9.2")
