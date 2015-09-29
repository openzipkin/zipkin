import com.twitter.zipkin.builder.QueryServiceBuilder
import com.twitter.zipkin.storage.anormdb._

val serverPort = sys.env.get("QUERY_PORT").getOrElse("9411").toInt
val adminPort = sys.env.get("QUERY_ADMIN_PORT").getOrElse("9901").toInt
val logLevel = sys.env.get("QUERY_LOG_LEVEL").getOrElse("INFO")

val db = DB(DBConfig("mysql", new DBParams(
  dbName = "zipkin",
  sys.env.get("MYSQL_HOST").getOrElse("localhost"),
  sys.env.get("MYSQL_TCP_PORT").map(_.toInt),
  sys.env.get("MYSQL_USER").getOrElse(""),
  sys.env.get("MYSQL_PASS").getOrElse(""))))

// Note: schema must be present prior to starting the collector or query
//   - zipkin-anormdb/src/main/resources/mysql.sql
val spanStore = new AnormSpanStore(db, None)
val dependencies = AnormDependencyStore(db)

QueryServiceBuilder(
  "0.0.0.0:" + serverPort,
  adminPort,
  logLevel,
  spanStore,
  dependencies
)
