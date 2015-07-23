import io.zipkin.sbt._

// Change this to one of the keys in anormDriverDependencies
// to use another database engine
val dbEngine =  "sqlite-persistent"

defaultSettings

// Database drivers
val anormDriverDependencies = Map(
  "sqlite-memory"     -> "org.xerial"     % "sqlite-jdbc"          % "3.7.2",
  "sqlite-persistent" -> "org.xerial"     % "sqlite-jdbc"          % "3.7.2",
  "h2-memory"         -> "com.h2database" % "h2"                   % "1.3.172",
  "h2-persistent"     -> "com.h2database" % "h2"                   % "1.3.172",
  "postgresql"        -> "postgresql"     % "postgresql"           % "8.4-702.jdbc4", // or "9.1-901.jdbc4",
  "mysql"             -> "mysql"          % "mysql-connector-java" % "5.1.25"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "anorm" % "2.3.7",
  "com.zaxxer" % "HikariCP-java6" % "2.3.8",
  anormDriverDependencies(dbEngine)
) ++ testDependencies

addConfigsToResourcePathForConfigSpec()

resolvers += Resolver.typesafeRepo("releases") // for anorm
