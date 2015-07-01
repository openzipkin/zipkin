/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
import com.twitter.sbt.{BuildProperties,PackageDist,GitProject}
import sbt._
import com.twitter.scrooge.ScroogeSBT
import sbt.Keys._
import Keys._
import Tests._
import sbtassembly.Plugin._
import AssemblyKeys._
import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.site.SphinxSupport.Sphinx

object Zipkin extends Build {
  val zipkinVersion = "1.2.0-SNAPSHOT"

  val finagleVersion = "6.26.0"
  val utilVersion = "6.25.0"
  val scroogeVersion = "3.19.0"
  val zookeeperVersions = Map(
    "candidate" -> "0.0.41",
    "group" -> "0.0.44",
    "client" -> "0.0.35",
    "server-set" -> "1.0.36"
  )

  val ostrichVersion = "9.9.0"
  val algebirdVersion  = "0.10.2"
  val scaldingVersion = "0.11.2"
  val hbaseVersion = "0.98.3-hadoop2"
  val hadoopVersion = "2.4.0"

  def finagle(name: String) = "com.twitter" %% ("finagle-" + name) % finagleVersion
  def util(name: String) = "com.twitter" %% ("util-" + name) % utilVersion
  def scroogeDep(name: String) = "com.twitter" %% ("scrooge-" + name) % scroogeVersion
  def algebird(name: String) = "com.twitter" %% ("algebird-" + name) % algebirdVersion
  def zk(name: String) = "com.twitter.common.zookeeper" % name % zookeeperVersions(name)

  val twitterServer = "com.twitter" %% "twitter-server" % "1.11.0"

  val proxyRepo = Option(System.getenv("SBT_PROXY_REPO"))
  val travisCi = Option(System.getenv("SBT_TRAVIS_CI")) // for adding travis ci maven repos before others
  val cwd = System.getProperty("user.dir")

  lazy val scalaTestDeps = Seq(
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "junit" % "junit" % "4.12" % "test"
  )

  lazy val testDependencies = Seq(
    "org.jmock"               %  "jmock"        % "2.4.0" % "test",
    "org.hamcrest"            %  "hamcrest-all" % "1.1"   % "test",
    "cglib"                   %  "cglib"        % "2.2.2" % "test",
    "asm"                     %  "asm"          % "1.5.3" % "test",
    "org.objenesis"           %  "objenesis"    % "1.1"   % "test",
    "org.scala-tools.testing" %% "specs"        % "1.6.9" % "test" cross CrossVersion.binaryMapped {
      case "2.9.2" => "2.9.1"
      case "2.10.5" => "2.10"
      case x => x
    },
    "junit" % "junit" % "4.12" % "test"
  )

  def zipkinSettings = Seq(
    organization := "com.twitter",
    version := zipkinVersion,
    crossScalaVersions := Seq("2.10.5"),
    scalaVersion := "2.10.5",
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

  // Database drivers
  val anormDriverDependencies = Map(
    "sqlite-memory"     -> "org.xerial"     % "sqlite-jdbc"          % "3.7.2",
    "sqlite-persistent" -> "org.xerial"     % "sqlite-jdbc"          % "3.7.2",
    "h2-memory"         -> "com.h2database" % "h2"                   % "1.3.172",
    "h2-persistent"     -> "com.h2database" % "h2"                   % "1.3.172",
    "postgresql"        -> "postgresql"     % "postgresql"           % "8.4-702.jdbc4", // or "9.1-901.jdbc4",
    "mysql"             -> "mysql"          % "mysql-connector-java" % "5.1.25"
  )

  lazy val zipkin =
    Project(
      id = "zipkin",
      base = file("."),
      settings = Project.defaultSettings ++
        defaultSettings ++
        Unidoc.settings
    ) aggregate(
      tracegen, common, scrooge, zookeeper,
      query, queryCore, queryService, web, zipkinAggregate,
      collectorScribe, collectorCore, collectorService,
      sampler, receiverScribe, receiverKafka, collector,
      cassandra, anormDB, kafka, redis, hbase
    )

  lazy val tracegen = Project(
    id = "zipkin-tracegen",
    base = file("zipkin-tracegen"),
    settings = defaultSettings
  ).dependsOn(queryService, collectorService)

  lazy val common =
    Project(
      id = "zipkin-common",
      base = file("zipkin-common"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        finagle("ostrich4"),
        finagle("thrift"),
        finagle("zipkin"),
        finagle("exception"),
        util("core"),
        zk("client"),
        algebird("core"),
        "com.twitter" %% "ostrich" % ostrichVersion
      ) ++ scalaTestDeps
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

  lazy val sampler =
    Project(
      id = "zipkin-sampler",
      base = file("zipkin-sampler"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        finagle("core"),
        finagle("http"),
        util("core"),
        util("zk")
      ) ++ scalaTestDeps
    ).dependsOn(common, zookeeper)

  lazy val scrooge =
    Project(
      id = "zipkin-scrooge",
      base = file("zipkin-scrooge"),
      settings = defaultSettings ++ ScroogeSBT.newSettings
    ).settings(
        ScroogeSBT.scroogeThriftSourceFolder in Compile <<= (baseDirectory in ThisBuild)
          (_ / "zipkin-thrift" / "src" / "main" / "thrift" / "com" / "twitter" / "zipkin" ),
        libraryDependencies ++= Seq(
        finagle("ostrich4"),
        finagle("thrift"),
        finagle("zipkin"),
        util("core"),
        scroogeDep("core"),
        scroogeDep("serializer"),
        algebird("core"),
        "com.twitter" %% "ostrich" % ostrichVersion
      ) ++ scalaTestDeps
    ).dependsOn(common)

  lazy val zookeeper = Project(
    id = "zipkin-zookeeper",
    base = file("zipkin-zookeeper"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= Seq(
      finagle("core"),
      util("core"),
      util("zk"),
      zk("candidate"),
      zk("group")
    )
  )

  lazy val collectorCore = Project(
    id = "zipkin-collector-core",
    base = file("zipkin-collector-core"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= Seq(
      finagle("ostrich4"),
      finagle("serversets"),
      finagle("thrift"),
      finagle("zipkin"),
      util("core"),
      util("zk"),
      util("zk-common"),
      zk("candidate"),
      zk("group"),
      algebird("core"),
      twitterServer,
      "com.twitter" %% "ostrich" % ostrichVersion
    ) ++ testDependencies
  ).dependsOn(common, scrooge)

  lazy val cassandra = Project(
    id = "zipkin-cassandra",
    base = file("zipkin-cassandra"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.6",
      finagle("serversets"),
      util("logging"),
      util("app"),
      scroogeDep("serializer"),
      "org.iq80.snappy" % "snappy" % "0.1",
      "org.mockito" % "mockito-all" % "1.9.5" % "test",
      "com.twitter" %% "scalding-core" % scaldingVersion,
      "org.apache.hadoop" % "hadoop-client" % hadoopVersion
    ) ++ testDependencies ++ scalaTestDeps,

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }
  ).dependsOn(scrooge)

  lazy val anormDB = Project(
    id = "zipkin-anormdb",
    base = file("zipkin-anormdb"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "anorm" % "2.3.7",
      "org.apache.commons" % "commons-dbcp2" % "2.1",
      anormDriverDependencies("sqlite-persistent")
    ) ++ scalaTestDeps,

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }
  ).dependsOn(common, scrooge)

  lazy val query =
    Project(
      id = "zipkin-query",
      base = file("zipkin-query"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        finagle("thriftmux"),
        finagle("zipkin"),
        util("app"),
        util("core")
      ) ++ scalaTestDeps
    ).dependsOn(common, scrooge)

  lazy val queryCore =
    Project(
      id = "zipkin-query-core",
      base = file("zipkin-query-core"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        finagle("ostrich4"),
        finagle("serversets"),
        finagle("thrift"),
        finagle("zipkin"),
        util("core"),
        util("zk"),
        util("zk-common"),
        zk("candidate"),
        zk("group"),
        algebird("core"),
        "com.twitter" %% "ostrich" % ostrichVersion
      ) ++ testDependencies
    ).dependsOn(common, query, scrooge)

  lazy val queryService = Project(
    id = "zipkin-query-service",
    base = file("zipkin-query-service"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= testDependencies,

    PackageDist.packageDistZipName := "zipkin-query-service.zip",
    BuildProperties.buildPropertiesPackage := "com.twitter.zipkin",
    resourceGenerators in Compile <+= BuildProperties.buildPropertiesWrite,

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }
  ).dependsOn(queryCore, cassandra, redis, anormDB, hbase)

  lazy val collectorScribe =
    Project(
      id = "zipkin-collector-scribe",
      base = file("zipkin-collector-scribe"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        scroogeDep("serializer")
      ) ++ testDependencies
    ).dependsOn(collectorCore, scrooge)

  lazy val collector = Project(
    id = "zipkin-collector",
    base = file("zipkin-collector"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= Seq(
      finagle("core"),
      util("core"),
      twitterServer
    ) ++ scalaTestDeps
  ).dependsOn(common, scrooge)

  lazy val receiverScribe =
    Project(
      id = "zipkin-receiver-scribe",
      base = file("zipkin-receiver-scribe"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        finagle("thriftmux"),
        util("zk"),
        "org.slf4j" % "slf4j-log4j12" % "1.6.4" % "runtime"
      ) ++ scalaTestDeps
    ).dependsOn(collector, zookeeper, scrooge)

  lazy val receiverKafka =
    Project(
      id = "zipkin-receiver-kafka",
      base = file("zipkin-receiver-kafka"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        twitterServer,
        "org.apache.kafka" %% "kafka" % "0.8.1.1",
        "org.apache.commons" % "commons-io" % "1.3.2",
        scroogeDep("serializer")
      ) ++ scalaTestDeps
    ).dependsOn(common, collector, zookeeper, scrooge)

  lazy val kafka =
    Project(
      id = "zipkin-kafka",
      base = file("zipkin-kafka"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        "com.twitter" %% "tormenta-kafka" % "0.8.0",
        scroogeDep("serializer")
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
    resourceGenerators in Compile <+= BuildProperties.buildPropertiesWrite,

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }
  ).dependsOn(collectorCore, collectorScribe, receiverKafka, cassandra, kafka, redis, anormDB, hbase)

  lazy val zipkinAggregate =
    Project(
      id = "zipkin-aggregate",
      base = file("zipkin-aggregate"),
      settings = defaultSettings ++ assemblySettings
    ).settings(
      mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
        {
          case PathList("org", xs @ _*) => MergeStrategy.first
          case PathList("com", xs @ _*) => MergeStrategy.first
          case "BUILD" => MergeStrategy.first
          case PathList(ps @_*) if ps.last == "package-info.class" => MergeStrategy.discard
          case x => old(x)
        }
      },
      BuildProperties.buildPropertiesPackage := "com.twitter.zipkin",
      resourceGenerators in Compile <+= BuildProperties.buildPropertiesWrite
  ).dependsOn(cassandra, common)



  lazy val web =
    Project(
      id = "zipkin-web",
      base = file("zipkin-web"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        finagle("exception"),
        finagle("thriftmux"),
        finagle("serversets"),
        finagle("zipkin"),
        zk("server-set"),
        algebird("core"),
        twitterServer,
        "com.github.spullara.mustache.java" % "compiler" % "0.8.13",
        "com.twitter.common" % "stats-util" % "0.0.42"
      ) ++ scalaTestDeps,

      PackageDist.packageDistZipName := "zipkin-web.zip",
      BuildProperties.buildPropertiesPackage := "com.twitter.zipkin",
      resourceGenerators in Compile <+= BuildProperties.buildPropertiesWrite,

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
      finagle("redis"),
      util("logging"),
      scroogeDep("serializer"),
      "org.slf4j" % "slf4j-log4j12" % "1.6.4" % "runtime"
    ) ++ testDependencies,

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }
  ).dependsOn(scrooge)

  lazy val hbaseTestGuavaHack = Project(
    id = "zipkin-hbase-test-guava-hack",
    base = file("zipkin-hbase/src/test/guava-hack"),
    settings = defaultSettings
  )
  lazy val hbase = Project(
    id = "zipkin-hbase",
    base = file("zipkin-hbase"),
    settings = defaultSettings
  ).settings(
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "org.apache.hbase"      % "hbase"                             % hbaseVersion,
      "org.apache.hbase"      % "hbase-common"                      % hbaseVersion,
      "org.apache.hbase"      % "hbase-common"                      % hbaseVersion % "test" classifier("tests") classifier(""),
      "org.apache.hbase"      % "hbase-client"                      % hbaseVersion,
      "org.apache.hbase"      % "hbase-client"                      % hbaseVersion % "test" classifier("tests") classifier(""),
      "org.apache.hbase"      % "hbase-server"                      % hbaseVersion % "test" classifier("tests") classifier(""),
      "org.apache.hbase"      % "hbase-hadoop-compat"               % hbaseVersion % "test" classifier("tests") classifier(""),
      "org.apache.hbase"      % "hbase-hadoop2-compat"              % hbaseVersion % "test" classifier("tests") classifier(""),
      "com.google.guava"      % "guava"                             % "11.0.2" % "test", //Hadoop needs a deprecated class
      "com.google.guava"      % "guava-io"                          % "r03" % "test", //Hadoop needs a deprecated class
      "com.google.protobuf"   % "protobuf-java"                     % "2.4.1",
      "org.apache.hadoop"     % "hadoop-common"                     % hadoopVersion,
      "org.apache.hadoop"     % "hadoop-mapreduce-client-jobclient" % hadoopVersion % "test" classifier("tests") classifier(""),
      "org.apache.hadoop"     % "hadoop-common"                     % hadoopVersion % "test" classifier("tests"),
      "org.apache.hadoop"     % "hadoop-hdfs"                       % hadoopVersion % "test" classifier("tests") classifier(""),
      "commons-logging"       % "commons-logging"                   % "1.1.1",
      "commons-configuration" % "commons-configuration"             % "1.6",
      "org.apache.zookeeper"  % "zookeeper"                         % "3.4.6" % "runtime" notTransitive(),
      "org.slf4j"             % "slf4j-log4j12"                     % "1.6.4" % "runtime",
      util("logging"),
      scroogeDep("serializer")
    )  ++ testDependencies ++ scalaTestDeps,

    resolvers ~= {rs => Seq(DefaultMavenRepository) ++ rs},

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }
  ).dependsOn(scrooge, hbaseTestGuavaHack % "test->compile")

  lazy val example = Project(
    id = "zipkin-example",
    base = file("zipkin-example"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= Seq(
      finagle("zipkin"),
      finagle("stats"),
      twitterServer
    )
  ).dependsOn(
    tracegen, web, anormDB, query,
    receiverScribe, zookeeper
  )

  lazy val zipkinDoc = Project(
    id = "zipkin-doc",
    base = file("doc"),
    settings = Project.defaultSettings ++ site.settings ++ site.sphinxSupport() ++ defaultSettings ++ Seq(
      scalacOptions in doc <++= (version).map(v => Seq("-doc-title", "Zipkin", "-doc-version", v)),
      includeFilter in Sphinx := ("*.html" | "*.jpg" | "*.png" | "*.svg" | "*.js" | "*.css" | "*.gif" | "*.txt"),

      // Workaround for sbt bug: Without a testGrouping for all test configs,
      // the wrong tests are run
      testGrouping <<= definedTests in Test map partitionTests,
      testGrouping in DocTest <<= definedTests in DocTest map partitionTests

    )).configs(DocTest).settings(inConfig(DocTest)(Defaults.testSettings): _*).settings(
      unmanagedSourceDirectories in DocTest <+= baseDirectory { _ / "src/sphinx/code" },
      //resourceDirectory in DocTest <<= baseDirectory { _ / "src/test/resources" }

      // Make the "test" command run both, test and doctest:test
      test <<= Seq(test in Test, test in DocTest).dependOn
    )//.dependsOn(finagleCore, finagleHttp)

  /* Test Configuration for running tests on doc sources */
  lazy val DocTest = config("doctest") extend(Test)

  // A dummy partitioning scheme for tests
  def partitionTests(tests: Seq[TestDefinition]) = {
    Seq(new Group("inProcess", tests, InProcess))
  }
}
