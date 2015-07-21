import AssemblyKeys._
import io.zipkin.sbt._

defaultSettings ++ assemblySettings

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList("org", xs @ _*) => MergeStrategy.first
    case PathList("com", xs @ _*) => MergeStrategy.first
    case "BUILD" => MergeStrategy.first
    case PathList(ps @_*) if ps.last == "package-info.class" => MergeStrategy.discard
    case x => old(x)
  }
}

BuildProperties.buildPropertiesPackage := "com.twitter.zipkin"

resourceGenerators in Compile <+= BuildProperties.buildPropertiesWrite
