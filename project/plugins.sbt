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
      "twitter.com" at "http://maven.twttr.com/",
      "scala-tools" at "http://scala-tools.org/repo-releases/",
      "maven" at "http://repo1.maven.org/maven2/",
      "freemarker" at "http://freemarker.sourceforge.net/maven2/",
      Resolver.url("artifactory", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
    )
  }) ++ Seq("local" at ("file:" + System.getProperty("user.home") + "/.m2/repository/"))
}

externalResolvers <<= (resolvers) map identity

addSbtPlugin("com.twitter" % "sbt-package-dist" % "1.0.5")

addSbtPlugin("com.twitter" % "sbt11-scrooge" % "3.0.0")

addSbtPlugin("com.twitter" % "sbt-thrift2" % "0.0.1")

//addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.8.2")
