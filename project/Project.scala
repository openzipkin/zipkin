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
import io.zipkin.sbt.{BuildProperties,PackageDist,GitProject}
import sbt._
import Keys._

object Zipkin extends Build {
  val zipkinVersion = "1.2.0-SNAPSHOT"

  ///////////////////////
  // Build environment //
  ///////////////////////

  val proxyRepo = Option(System.getenv("SBT_PROXY_REPO"))
  val travisCi = Option(System.getenv("SBT_TRAVIS_CI")) // for adding travis ci maven repos before others
  val cwd = System.getProperty("user.dir")

  ////////////////////////////////
  // Commonly used dependencies //
  ////////////////////////////////

  def finagle(name: String) = "com.twitter" %% ("finagle-" + name) % "6.27.0"
  def util(name: String) = "com.twitter" %% ("util-" + name) % "6.26.0"
  def scroogeDep(name: String) = "com.twitter" %% ("scrooge-" + name) % "3.20.0"
  def algebird(name: String) = "com.twitter" %% ("algebird-" + name) % "0.10.2"
  def hadoop(name: String) = "org.apache.hadoop" % ("hadoop-" + name) % "2.4.0"
  def hadoopTest(name: String) = hadoop(name) classifier("tests") classifier("")

  // Instead of Seq(util("foo"), util("bar"), ...) we can now write many(util, "foo", "bar", ...)
  def many(gen: (String => ModuleID), names: String*) = names map gen

  val twitterZookeeperVersions = Map(
    "candidate" -> "0.0.75",
    "group" -> "0.0.81",
    "client" -> "0.0.70",
    "server-set" -> "1.0.103"
  )
  def zk(name: String) = "com.twitter.common.zookeeper" % name % twitterZookeeperVersions(name)

  val twitterServer = "com.twitter" %% "twitter-server" % "1.12.0"
  val junit = "junit" % "junit" % "4.12" % "test"
  val slf4jLog4j12 = "org.slf4j" % "slf4j-log4j12" % "1.6.4" % "runtime"
  val ostrich = "com.twitter" %% "ostrich" % "9.10.0"

  lazy val testDependencies = Seq(
    junit,
    "org.mockito"              % "mockito-all" % "1.10.19" % "test",
    "org.scalatest"           %% "scalatest"   % "2.2.5"   % "test"
  )

  /////////////////////
  // Common settings //
  /////////////////////

  def zipkinSettings = Seq(
    organization := "io.zipkin",
    version := zipkinVersion,
    crossScalaVersions := Seq("2.10.5"),
    scalaVersion := "2.10.5",
    crossPaths := false,            /* Removes Scala version from artifact name */
    fork := true, // forking prevents runaway thread pollution of sbt
    baseDirectory in run := file(cwd), // necessary for forking
    resolvers ++= Seq(
      Resolver.jcenterRepo, // superset of maven central
      "Twitter Maven Repository" at "https://maven.twttr.com/" // for thrift 0.5.0-1 needed by scrooge
    ),
    dependencyOverrides ++= Set(
      "org.apache.zookeeper" % "zookeeper" % "3.4.6", // internal twitter + kafka + curator
      "org.slf4j" % "slf4j-api" % "1.6.4" // libthrift 0.5 otherwise pins 1.5.x
    )
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
    Project.defaultSettings
  ).flatten

  ///////////////////////////
  // Misc helper functions //
  ///////////////////////////

  def subproject(name: String, deps: ClasspathDep[ProjectReference]*) = {
    val id = "zipkin-" + name
    Project(
      id = id,
      base = file(id)
    ).dependsOn(deps:_*)
  }

  def addConfigsToResourcePathForConfigSpec() =
    unmanagedResourceDirectories in Test <<= baseDirectory {
      base =>
        (base / "config" +++ base / "src" / "test" / "resources").get
    }

  ////////////////////////////////////
  // Zipkin's mostly common library //
  ////////////////////////////////////
  // Not all projects depend on this!
  lazy val common = subproject("common")

  ///////////////////////////////////////////////////////////////////////////////////////////
  // Projects with outputs that are useful on their own: documentation, examples, services //
  ///////////////////////////////////////////////////////////////////////////////////////////
  // These are usually the projects you want to `sbt package`

  // The Project
  lazy val zipkin = project.in(file(".")) aggregate(
    tracegen, common, scrooge, zookeeper,
    query, queryCore, queryService, web, zipkinAggregate,
    collectorScribe, collectorCore, collectorService,
    sampler, receiverScribe, receiverKafka, collector,
    cassandra, anormDB, redis, mongodb
    )


  lazy val zipkinDoc = Project(
    id = "zipkin-doc",
    base = file("doc"))//.dependsOn(finagleCore, finagleHttp)

  lazy val example = subproject(
      "example",
      tracegen, web, anormDB, query,
      receiverScribe, zookeeper
    )

  lazy val redisExample = subproject(
    "redis-example",
    web, redis, query,
    receiverScribe, zookeeper
  )

  lazy val collectorService = subproject(
    "collector-service",
    collectorCore, collectorScribe, receiverKafka,
    cassandra, redis, anormDB, mongodb)

  lazy val queryService = subproject(
    "query-service",
    queryCore, cassandra, redis, anormDB, mongodb)

  lazy val web = subproject("web", common, scrooge)

  lazy val zipkinAggregate = subproject("aggregate", cassandra, common)

  /////////////////////////////////////
  // Collector, and Collector inputs //
  /////////////////////////////////////

  lazy val collector = subproject("collector", common, scrooge)
  lazy val collectorCore = subproject("collector-core", common, scrooge)
  lazy val collectorScribe = subproject("collector-scribe", collectorCore, scrooge)

  ///////////////
  // Receivers //
  ///////////////

  lazy val receiverScribe = subproject("receiver-scribe", collector, zookeeper, scrooge)
  lazy val receiverKafka = subproject("receiver-kafka", common, collector, zookeeper, scrooge)

  /////////////////
  // Query logic //
  /////////////////

  lazy val query = subproject("query", common, scrooge)
  lazy val queryCore = subproject("query-core", common, query, scrooge)

  /////////////////////////////////////////////////////
  // Libraries wrapping clients to external services //
  /////////////////////////////////////////////////////

  lazy val zookeeper = subproject("zookeeper")
  lazy val cassandra = subproject("cassandra", scrooge)
  lazy val anormDB = subproject("anormdb", common, scrooge)
  lazy val redis = subproject("redis", common, scrooge)
  lazy val mongodb = subproject("mongodb", common, scrooge)

  ////////////
  // Thrift //
  ////////////

  lazy val thriftidl = subproject("thrift")
  lazy val scrooge = subproject("scrooge", common)

  //////////
  // Misc //
  //////////

  lazy val tracegen = subproject("tracegen", queryService, collectorService)
  lazy val sampler = subproject("sampler", common, zookeeper)
}
