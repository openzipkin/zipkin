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
import sbtassembly.Plugin._
import AssemblyKeys._

object Zipkin extends Build {
  val zipkinVersion = "1.2.0-SNAPSHOT"

  val finagleVersion = "6.12.2"
  val utilVersion = "6.12.1"
  val scroogeVersion = "3.12.2"
  val cassieVersion = "0.25.3"
  val zookeeperVersions = Map(
    "candidate" -> "0.0.41",
    "group" -> "0.0.44",
    "client" -> "0.0.35",
    "server-set" -> "1.0.36"
  )

  val ostrichVersion = "9.2.1"
  val algebirdVersion  = "0.4.0"
  val hbaseVersion = "0.94.10"
  val summingbirdVersion = "0.3.2"

  def finagle(name: String) = "com.twitter" % ("finagle-" + name + "_2.9.2") % finagleVersion
  def util(name: String) = "com.twitter" % ("util-" + name + "_2.9.2") % utilVersion
  def scroogeDep(name: String) = "com.twitter" % ("scrooge-" + name + "_2.9.2") % scroogeVersion
  def algebird(name: String) = "com.twitter" %% ("algebird-" + name) % algebirdVersion
  def zk(name: String) = "com.twitter.common.zookeeper" % name % zookeeperVersions(name)

  val twitterServer = "com.twitter" % "twitter-server_2.9.2" % "1.4.0"

  // cassie brings in old versions of finagle and util. we need to exclude here and bring in exclusive versions
  def cassie(name: String) =
    "com.twitter" % ("cassie-" + name) % cassieVersion excludeAll(
      ExclusionRule(organization = "com.twitter", name = "finagle-core"),
      ExclusionRule(organization = "com.twitter", name = "finagle-serversets"),
      ExclusionRule(organization = "com.twitter", name = "finagle-thrift"),
      ExclusionRule(organization = "com.twitter", name = "util-core")
    )

  val proxyRepo = Option(System.getenv("SBT_PROXY_REPO"))
  val travisCi = Option(System.getenv("SBT_TRAVIS_CI")) // for adding travis ci maven repos before others
  val cwd = System.getProperty("user.dir")

  lazy val scalaTestDeps = Seq(
    "org.scalatest" %% "scalatest" % "1.9.1" % "test",
    "junit" % "junit" % "4.10" % "test"
  )

  lazy val testDependencies = Seq(
    "org.jmock"               %  "jmock"        % "2.4.0" % "test",
    "org.hamcrest"            %  "hamcrest-all" % "1.1"   % "test",
    "cglib"                   %  "cglib"        % "2.2.2" % "test",
    "asm"                     %  "asm"          % "1.5.3" % "test",
    "org.objenesis"           %  "objenesis"    % "1.1"   % "test",
    "org.scala-tools.testing" %% "specs"        % "1.6.9" % "test" cross CrossVersion.binaryMapped {
      case "2.9.2" => "2.9.1"
      case "2.10.0" => "2.10"
      case x => x
    },
    "junit" % "junit" % "4.10" % "test"
  )

  def zipkinSettings = Seq(
    organization := "com.twitter",
    version := zipkinVersion,
    crossScalaVersions := Seq("2.9.3"),
    scalaVersion := "2.9.3",
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
      base = file(".")
    ) aggregate(
      tracegen, common, scrooge, zookeeper,
      query, queryCore, queryService, web,
      collectorScribe, collectorCore, collectorService,
      sampler, receiverScribe, collector,
      cassandra, anormDB, kafka, redis, hbase, storm
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
        "com.twitter" % "ostrich_2.9.2" % ostrichVersion
      ) ++ testDependencies ++ scalaTestDeps
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
        libraryDependencies ++= Seq(
        finagle("ostrich4"),
        finagle("thrift"),
        finagle("zipkin"),
        util("core"),
        scroogeDep("core"),
        scroogeDep("serializer"),
        algebird("core"),
        "com.twitter" % "ostrich_2.9.2" % ostrichVersion
      ) ++ testDependencies
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
      "com.twitter" % "ostrich_2.9.2" % ostrichVersion
    ) ++ testDependencies
  ).dependsOn(common, scrooge)

  lazy val cassandra = Project(
    id = "zipkin-cassandra",
    base = file("zipkin-cassandra"),
    settings = defaultSettings
  ).settings(
    libraryDependencies ++= Seq(
      cassie("core"),
      cassie("serversets"),
      finagle("serversets"),
      util("logging"),
      util("app"),
      scroogeDep("serializer"),
      "org.iq80.snappy" % "snappy" % "0.1"
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
      "play" % "anorm_2.9.2" % "2.1-09142012",
      anormDriverDependencies("sqlite-persistent")
    ) ++ testDependencies ++ scalaTestDeps,

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
        "com.twitter" % "ostrich_2.9.2" % ostrichVersion
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
        util("zk"),
        "org.slf4j" % "slf4j-log4j12" % "1.6.4" % "runtime"
      ) ++ scalaTestDeps
    ).dependsOn(collector, zookeeper, scrooge)

  lazy val kafka =
    Project(
      id = "zipkin-kafka",
      base = file("zipkin-kafka"),
      settings = defaultSettings
    ).settings(
      libraryDependencies ++= Seq(
        "com.twitter"      % "kafka_2.9.2"    % "0.7.0",
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
  ).dependsOn(collectorCore, collectorScribe, cassandra, kafka, redis, anormDB, hbase)

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
        "com.github.spullara.mustache.java" % "compiler" % "0.8.13"
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

  lazy val hbase = Project(
    id = "zipkin-hbase",
    base = file("zipkin-hbase"),
    settings = defaultSettings
  ).settings(
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "org.apache.hbase"      % "hbase"                 % hbaseVersion notTransitive(),
      "org.apache.hbase"      % "hbase"                 % hbaseVersion % "test" classifier("tests") classifier(""),
      "com.google.guava"      % "guava-io"              % "r03" % "test",
      "com.google.protobuf"   % "protobuf-java"         % "2.4.1",
      "org.apache.hadoop"     % "hadoop-core"           % "1.1.2" notTransitive(),
      "org.apache.hadoop"     % "hadoop-test"           % "1.1.2" % "test",
      "commons-logging"       % "commons-logging"       % "1.1.1",
      "commons-configuration" % "commons-configuration" % "1.6",
      "org.apache.zookeeper"  % "zookeeper"             % "3.4.5" % "runtime" notTransitive(),
      "org.slf4j"             % "slf4j-log4j12"         % "1.6.4" % "runtime",
      util("logging"),
      scroogeDep("serializer")
    ) ++ testDependencies,

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }
  ).dependsOn(scrooge)

  lazy val storm = Project(
    id = "zipkin-storm",
    base = file("zipkin-storm"),
    settings = defaultSettings ++ assemblySettings
  ).settings(
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      util("logging"),
      scroogeDep("serializer"),
      "commons-logging"       % "commons-logging"       % "1.1.1",
      "commons-configuration" % "commons-configuration" % "1.6",
      "com.twitter"           %% "bijection-core"       % "0.6.0",
      "com.twitter"           %% "bijection-scrooge"    % "0.6.0",
      "com.twitter"           %% "storehaus-memcache"   % "0.8.0",
      "com.twitter"           %% "summingbird-core"     % summingbirdVersion,
      "com.twitter"           %% "summingbird-batch"    % summingbirdVersion,
      "com.twitter"           %% "tormenta-kafka"       % "0.6.1" exclude("org.slf4j", "log4j-over-slf4j") exclude("ch.qos.logback", "logback-classic"),
      "storm"                 % "storm"                 % "0.9.0.1" % "provided",
      "storm"                 % "storm-kafka"           % "0.9.0-wip16a-scala292"
    ) ++ scalaTestDeps,

    publishArtifact in packageDoc := false,

    PackageDist.packageDistZipName := "zipkin-storm.zip",
    BuildProperties.buildPropertiesPackage := "com.twitter.zipkin",
    resourceGenerators in Compile <+= BuildProperties.buildPropertiesWrite,

    /* Add configs to resource path for ConfigSpec */
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    },

    excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
      cp filter {_.data.getName == "netty-3.2.3.Final.jar"}
    },

    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
      {
        case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
        case PathList("com", "fasterxml", xs @ _*) => MergeStrategy.last
        case PathList("com", "twitter", xs @ _*) => MergeStrategy.last
        case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
        case PathList("org", "apache", xs @ _*) => MergeStrategy.last
        case PathList("org", "objectweb", xs @ _*) => MergeStrategy.last
        case PathList("org", "slf4j", xs @ _*) => MergeStrategy.last
        case "project.clj" => MergeStrategy.last
        case x => old(x)
      }
    }
  ).dependsOn(scrooge)

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
}
