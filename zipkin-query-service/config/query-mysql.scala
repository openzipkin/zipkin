import com.twitter.zipkin.anormdb.{DependencyStoreBuilder, SpanStoreBuilder}
import com.twitter.zipkin.builder.QueryServiceBuilder
import com.twitter.zipkin.storage.Store
import com.twitter.zipkin.storage.anormdb.{DB, DBConfig, DBParams}

val db = DB(DBConfig("mysql", new DBParams(
  dbName = "zipkin",
  sys.env.get("MYSQL_HOST").getOrElse("localhost"),
  sys.env.get("MYSQL_TCP_PORT").map(_.toInt),
  sys.env.get("MYSQL_USER").getOrElse(""),
  sys.env.get("MYSQL_PASS").getOrElse(""))))

// Note: schema must be present prior to starting the collector or query
//   - zipkin-anormdb/src/main/resources/mysql.sql
val storeBuilder = Store.Builder(SpanStoreBuilder(db), DependencyStoreBuilder(db))

QueryServiceBuilder(storeBuilder)
