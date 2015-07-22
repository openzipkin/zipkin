import io.zipkin.sbt._

defaultSettings

libraryDependencies ++= testDependencies

PackageDist.packageDistZipName := "zipkin-query-service.zip"
BuildProperties.buildPropertiesPackage := "com.twitter.zipkin"
resourceGenerators in Compile <+= BuildProperties.buildPropertiesWrite

addConfigsToResourcePathForConfigSpec()

