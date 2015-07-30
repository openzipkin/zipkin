import io.zipkin.sbt._

// Change this to one of the keys in anormDriverDependencies
// to use another database engine
val dbEngine =  "sqlite-persistent"

defaultSettings

// Database drivers
val anormDriverDependencies = Map(
  "sqlite-memory"     -> "org.xerial"     % "sqlite-jdbc"          % "3.8.11", // sqlite-jdbc4 isn't out, yet
  "sqlite-persistent" -> "org.xerial"     % "sqlite-jdbc"          % "3.8.11",
  "h2-memory"         -> "com.h2database" % "h2"                   % "1.4.187",
  "h2-persistent"     -> "com.h2database" % "h2"                   % "1.4.187",
  // Don't add EOL versions! http://www.postgresql.org/support/versioning
  "postgresql93"      -> "org.postgresql" % "postgresql"           % "9.3-1103-jdbc4",
  "mysql"             -> "mysql"          % "mysql-connector-java" % "5.1.36"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "anorm" % "2.3.9", // last version compatible w/ Java 7
  "com.zaxxer" % "HikariCP" % "2.4.0",
  anormDriverDependencies(dbEngine)
) ++ testDependencies

addConfigsToResourcePathForConfigSpec()

resolvers += Resolver.typesafeRepo("releases") // for anorm
