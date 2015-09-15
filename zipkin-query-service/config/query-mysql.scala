import com.twitter.logging.{ConsoleHandler, Level, LoggerFactory}
import com.twitter.zipkin.anormdb.{DependencyStoreBuilder, SpanStoreBuilder}
import com.twitter.zipkin.builder.{ZipkinServerBuilder, QueryServiceBuilder}
import com.twitter.zipkin.storage.Store
import com.twitter.zipkin.storage.anormdb.{DB, DBConfig, DBParams}

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
val storeBuilder = Store.Builder(SpanStoreBuilder(db), DependencyStoreBuilder(db))

val loggerFactory = new LoggerFactory(
  node = "",
  level = Level.parse(logLevel),
  handlers = List(ConsoleHandler())
)

QueryServiceBuilder(
  storeBuilder,
  serverBuilder = ZipkinServerBuilder(serverPort, adminPort).loggers(List(loggerFactory))
)