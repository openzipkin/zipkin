import com.twitter.sbt.{BuildProperties,PackageDist,GitProject}
import sbt._
import com.twitter.scrooge.ScroogeSBT
import sbt.Keys._

object Zipkin extends Build {

  val CASSIE_VERSION  = "0.25.2"
  val FINAGLE_VERSION = "6.4.0"
  val OSTRICH_VERSION = "9.1.1"
  val UTIL_VERSION    = "6.3.4"
  val SCROOGE_VERSION = "3.1.1"
  val ZOOKEEPER_VERSION = "0.0.30"
  val ALGEBIRD_VERSION  = "0.1.13"

  val proxyRepo = Option(System.getenv("SBT_PROXY_REPO"))
  val travisCi = Option(System.getenv("SBT_TRAVIS_CI")) // for adding travis ci maven repos before others
  val cwd = System.getProperty("user.dir")

  lazy val testDependencies = Seq(
    "org.scala-tools.testing" %% "specs"        % "1.6.9" % "test",
    "org.jmock"               %  "jmock"        % "2.4.0" % "test",
    "org.hamcrest"            %  "hamcrest-all" % "1.1"   % "test",
    "cglib"                   %  "cglib"        % "2.2.2" % "test",
    "asm"                     %  "asm"          % "1.5.3" % "test",
    "org.objenesis"           %  "objenesis"    % "1.1"   % "test",
    "junit"                   %  "junit"        % "4.7"   % "test"
  )

  def zipkinSettings = Seq(
    organization := "com.twitter",
    version := "1.1.0-SNAPSHOT",
    crossPaths := false,            /* Removes Scala version from artifact name */
    fork := true, // forking prevents runaway thread pollution of sbt
    baseDirectory in run := file(cwd), // necessary for forking
    publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath + "/.ivy2/local")))
  )

  // settings from inlined plugins
  def inlineSettings = Seq(
    // inlined parts of sbt-package-dist
    GitProject.gitSettings,
    BuildProperties.newSettings,
    PackageDist.newSettings,

    // modifications and additions
    Seq(
      (exportedProducts in Compile) ~= { products =>
        products.filter { prod =>
          // don't package source or documentation
          prod == (packageSrc in Compile) || prod == (packageDoc in Compile)
        }
      }
    )
  ).flatten

  def defaultSettings = Seq(
    zipkinSettings,
    inlineSettings,
    Project.defaultSettings,
    ZipkinResolver.newSettings
  ).flatten

  lazy val zipkin =
    Project(
      id = "zipkin",
      base = file(".")
    ) aggregate(test, queryCore, queryService, common, scrooge, collectorScribe, web, cassandra, collectorCore, collectorService, kafka) // TODO - add redis back in

  lazy val test   = Project(
    id = "zipkin-test",
    base = file("zipkin-test"),
    settings = defaultSettings
  ).settings(
    name := "zipkin-test",
    libraryDependencies ++= testDependencies
  ) dependsOn(queryService, collectorService)

  lazy val common =
    Project(
      id = "zipkin-common",
      base = file("zipkin-common"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        "com.twitter" % "finagle-ostrich4"  % FINAGLE_VERSION,
        "com.twitter" % "finagle-thrift"    % FINAGLE_VERSION,
        "com.twitter" % "finagle-zipkin"    % FINAGLE_VERSION,
        "com.twitter" % "finagle-exception" % FINAGLE_VERSION,
        "com.twitter" % "ostrich"           % OSTRICH_VERSION,
        "com.twitter" % "util-core"         % UTIL_VERSION,
        "com.twitter" %% "algebird-core"    % ALGEBIRD_VERSION,

        "com.twitter.common.zookeeper" % "client"    % ZOOKEEPER_VERSION
      ) ++ testDependencies
    )

  lazy val thriftidl =
    Project(
      id = "zipkin-thrift",
      base = file("zipkin-thrift"),
      settings = defaultSettings
    ).settings(
      // this is a hack to get -idl artifacts for thrift.  Better would be to
      // define a whole new artifact that gets included in the scrooge publish task
      (artifactClassifier in packageSrc) := Some("idl")
    )

  lazy val scrooge =
    Project(
      id = "zipkin-scrooge",
      base = file("zipkin-scrooge"),
      settings = defaultSettings ++ ScroogeSBT.newSettings
    ).settings(
        libraryDependencies ++= Seq(
        "com.twitter" %  "finagle-ostrich4"  % FINAGLE_VERSION,
        "com.twitter" %  "finagle-thrift"    % FINAGLE_VERSION,
        "com.twitter" %  "finagle-zipkin"    % FINAGLE_VERSION,
        "com.twitter" %  "ostrich"           % OSTRICH_VERSION,
        "com.twitter" %  "util-core"         % UTIL_VERSION,
        "com.twitter" %% "algebird-core"     % ALGEBIRD_VERSION,
        "com.twitter" %  "scrooge-runtime"   % SCROOGE_VERSION
      ) ++ testDependencies
    ).dependsOn(common)

  lazy val collectorCore = Project(
    id = "zipkin-collector-core",
    base = file("zipkin-collector-core"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= Seq(
      "com.twitter" % "finagle-ostrich4"  % FINAGLE_VERSION,
      "com.twitter" % "finagle-serversets"% FINAGLE_VERSION,
      "com.twitter" % "finagle-thrift"    % FINAGLE_VERSION,
      "com.twitter" % "finagle-zipkin"    % FINAGLE_VERSION,
      "com.twitter" % "ostrich"           % OSTRICH_VERSION,
      "com.twitter" %% "algebird-core"    % ALGEBIRD_VERSION,
      "com.twitter" % "util-core"         % UTIL_VERSION,
      "com.twitter" % "util-zk"           % UTIL_VERSION,
      "com.twitter" % "util-zk-common"    % UTIL_VERSION,

      "com.twitter.common.zookeeper" % "candidate" % ZOOKEEPER_VERSION,
      "com.twitter.common.zookeeper" % "group"     % ZOOKEEPER_VERSION
    ) ++ testDependencies
  ).dependsOn(common, scrooge)

  lazy val cassandra = Project(
    id = "zipkin-cassandra",
    base = file("zipkin-cassandra"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= Seq(
      "com.twitter"     % "cassie-core"       % CASSIE_VERSION,
      "com.twitter"     % "cassie-serversets" % CASSIE_VERSION,
      "com.twitter"     % "util-logging"      % UTIL_VERSION,
      "org.iq80.snappy" % "snappy"            % "0.1"
    ) ++ testDependencies,

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }
  ).dependsOn(scrooge)

  lazy val queryCore =
    Project(
      id = "zipkin-query-core",
      base = file("zipkin-query-core"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        "com.twitter" % "finagle-ostrich4"  % FINAGLE_VERSION,
        "com.twitter" % "finagle-serversets"% FINAGLE_VERSION,
        "com.twitter" % "finagle-thrift"    % FINAGLE_VERSION,
        "com.twitter" % "finagle-zipkin"    % FINAGLE_VERSION,
        "com.twitter" % "ostrich"           % OSTRICH_VERSION,
        "com.twitter" %% "algebird-core"    % ALGEBIRD_VERSION,
        "com.twitter" % "util-core"         % UTIL_VERSION,
        "com.twitter" % "util-zk"           % UTIL_VERSION,
        "com.twitter" % "util-zk-common"    % UTIL_VERSION,

        "com.twitter.common.zookeeper" % "candidate" % ZOOKEEPER_VERSION,
        "com.twitter.common.zookeeper" % "group"     % ZOOKEEPER_VERSION
      ) ++ testDependencies
    ).dependsOn(common, scrooge)

  lazy val queryService = Project(
    id = "zipkin-query-service",
    base = file("zipkin-query-service"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= testDependencies,

    PackageDist.packageDistZipName := "zipkin-query-service.zip",
    BuildProperties.buildPropertiesPackage := "com.twitter.zipkin",

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }
  ).dependsOn(queryCore, cassandra)

  lazy val collectorScribe =
    Project(
      id = "zipkin-collector-scribe",
      base = file("zipkin-collector-scribe"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= testDependencies
    ).dependsOn(collectorCore, scrooge)

  lazy val kafka =
    Project(
      id = "zipkin-kafka",
      base = file("zipkin-kafka"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        "org.clojars.jasonjckn"      %% "kafka"    % "0.7.2-test1"
      ) ++ testDependencies,
      resolvers ++= (proxyRepo match {
        case None => Seq(
          "clojars" at "http://clojars.org/repo")
        case Some(pr) => Seq() // if proxy is set we assume that it has the artifacts we would get from the above repo
      })
    ).dependsOn(collectorCore, scrooge)

  lazy val collectorService = Project(
    id = "zipkin-collector-service",
    base = file("zipkin-collector-service"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= testDependencies,

    PackageDist.packageDistZipName := "zipkin-collector-service.zip",
    BuildProperties.buildPropertiesPackage := "com.twitter.zipkin",

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }
  ).dependsOn(collectorCore, collectorScribe, cassandra, kafka)

  lazy val web =
    Project(
      id = "zipkin-web",
      base = file("zipkin-web"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        "com.twitter" % "finatra" % "1.3.1",

        "com.twitter.common.zookeeper" % "server-set" % "1.0.36",

        "com.twitter" % "finagle-serversets" % FINAGLE_VERSION,
        "com.twitter" % "finagle-zipkin"     % FINAGLE_VERSION,
        "com.twitter" % "finagle-exception"  % FINAGLE_VERSION,
        "com.twitter" %% "algebird-core"     % ALGEBIRD_VERSION
      ) ++ testDependencies,

      PackageDist.packageDistZipName := "zipkin-web.zip",
      BuildProperties.buildPropertiesPackage := "com.twitter.zipkin",

      /* Add configs to resource path for ConfigSpec */
      unmanagedResourceDirectories in Test <<= baseDirectory {
        base =>
          (base / "config" +++ base / "src" / "test" / "resources").get
      }
  ).dependsOn(common, scrooge)

  lazy val redis = Project(
    id = "zipkin-redis",
    base = file("zipkin-redis"),
    settings = defaultSettings
  ).settings(
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "com.twitter" % "finagle-redis"     % FINAGLE_VERSION,
      "org.slf4j" % "slf4j-log4j12"          % "1.6.4" % "runtime",
      "com.twitter"     % "util-logging"      % UTIL_VERSION
    ) ++ testDependencies,

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }
  ).dependsOn(scrooge)
}

