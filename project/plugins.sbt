sbtResolver <<= (sbtResolver) { r =>
  Option(System.getenv("SBT_PROXY_REPO")) map { x =>
    Resolver.url("proxy repo for sbt", url(x))(Resolver.ivyStylePatterns)
  } getOrElse r
}

resolvers <<= (resolvers) { r =>
  (Option(System.getenv("SBT_PROXY_REPO")) map { url =>
    Seq("proxy-repo" at url)
  } getOrElse {
    r ++ Seq(
      "travisci-central" at "http://maven.travis-ci.org/nexus/content/repositories/central/",
      "travisci-sonatype" at "http://maven.travis-ci.org/nexus/content/repositories/sonatype/",
      "twitter.com" at "http://maven.twttr.com/",
      "scala-tools" at "http://scala-tools.org/repo-releases/",
      "maven" at "http://repo1.maven.org/maven2/",
      "freemarker" at "http://freemarker.sourceforge.net/maven2/",
      Resolver.url("artifactory", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
    )
  }) ++ Seq("local" at ("file:" + System.getProperty("user.home") + "/.m2/repository/"))
}

libraryDependencies ++= Seq(
    "com.google.collections" % "google-collections" % "0.8",
    "org.codehaus.plexus"    % "plexus-utils"       % "1.5.4",
    "org.slf4j"              % "slf4j-api"          % "1.6.1",
    "org.slf4j"              % "slf4j-simple"       % "1.6.1",
    "com.twitter"            % "scrooge-generator"  % "3.1.1")

externalResolvers <<= (resolvers) map identity

