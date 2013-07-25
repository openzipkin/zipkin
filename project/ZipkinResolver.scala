import sbt._
import Keys._

/**
 * archive resolving gets a bit tricky depending on if we're compiling in github, twitter,
 * or somewhere else
 */
object ZipkinResolver extends Plugin {

  val proxyRepo = Option(System.getenv("SBT_PROXY_REPO"))
  val isTravisCi = "true".equalsIgnoreCase(System.getenv("SBT_TRAVIS_CI"))

  val defaultResolvers = SettingKey[Seq[Resolver]](
    "default-resolvers",
    "maven repositories to use by default, unless a proxy repo is set via SBT_PROXY_REPO"
  )

  val travisCiResolvers = SettingKey[Seq[Resolver]](
    "travisci-central",
    "Use these resolvers when building on travis-ci"
  )

  val localRepo = SettingKey[File](
    "local-repo",
    "local folder to use as a repo (and where publish-local publishes to)"
  )

  val newSettings = Seq(
    defaultResolvers := proxyRepo map { url =>
        // only resolve using an internal proxy if the env is set
        Seq("proxy-repo" at url)
    } getOrElse {
        // for everybody else
        Seq(
          // used for github continuous integration
          "travisci-central" at "http://maven.travis-ci.org/nexus/content/repositories/central/",
          "travisci-sonatype" at "http://maven.travis-ci.org/nexus/content/repositories/sonatype/",

          // standard resolvers
          "typesafe" at "http://repo.typesafe.com/typesafe/releases",
          "ibiblio" at "http://mirrors.ibiblio.org/pub/mirrors/maven2/",
          "twitter.com" at "http://maven.twttr.com/",
          "powermock-api" at "http://powermock.googlecode.com/svn/repo/",
          "scala-tools.org" at "http://scala-tools.org/repo-releases/",
          "testing.scala-tools.org" at "http://scala-tools.org/repo-releases/testing/",
          "oauth.net" at "http://oauth.googlecode.com/svn/code/maven",
          "download.java.net" at "http://download.java.net/maven/2/",
          "atlassian" at "https://m2proxy.atlassian.com/repository/public/",
          // for netty:
          "jboss" at "http://repository.jboss.org/nexus/content/groups/public/"
        )
    },

    travisCiResolvers := Seq(
      "travisci-central" at "http://maven.travis-ci.org/nexus/content/repositories/central/",
      "travisci-sonatype" at "http://maven.travis-ci.org/nexus/content/repositories/sonatype/"
    ),

    localRepo := file(System.getProperty("user.home") + "/.m2/repository"),

    // configure resolvers for the build
    resolvers <<= (
      resolvers,
      defaultResolvers,
      travisCiResolvers,
      localRepo
      ) { (resolvers, defaultResolvers, travisCiResolvers, localRepo) =>
      (proxyRepo map { url =>
        Seq("proxy-repo" at url)
      } getOrElse {
        (if (isTravisCi) travisCiResolvers else Seq.empty[Resolver]) ++ resolvers ++ defaultResolvers
      }) ++ Seq(
        // the local repo has to be in here twice, because sbt won't push to a "file:"
        // repo, but it won't read artifacts from a "Resolver.file" repo. (head -> desk)
        "local-lookup" at ("file:" + localRepo.getAbsolutePath),
        Resolver.file("local", localRepo)(Resolver.mavenStylePatterns)
      )
    },

    // don't add any special resolvers.
    externalResolvers <<= (resolvers) map identity
  )
}