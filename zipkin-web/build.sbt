import io.zipkin.sbt._

defaultSettings

libraryDependencies ++= Seq(
  Seq(
    zk("server-set"),
    algebird("core"),
    twitterServer,
    "com.github.spullara.mustache.java" % "compiler" % "0.8.13",
    "com.twitter.common" % "stats-util" % "0.0.57"
  ),
  many(finagle, "exception", "thriftmux", "serversets", "zipkin"),
  scalaTestDeps
).flatten

PackageDist.packageDistZipName := "zipkin-web.zip"
BuildProperties.buildPropertiesPackage := "com.twitter.zipkin"
resourceGenerators in Compile <+= BuildProperties.buildPropertiesWrite

addConfigsToResourcePathForConfigSpec()
