import com.twitter.zipkin.anormdb.{AggregatesBuilder, SpanStoreBuilder}
import com.twitter.zipkin.builder.QueryServiceBuilder
import com.twitter.zipkin.storage.Store
import com.twitter.zipkin.storage.anormdb.{DB, DBConfig, DBParams}

val db = DB(DBConfig("mysql", new DBParams(
  dbName = "zipkin",
  sys.env.get("MYSQL_HOST").getOrElse("localhost"),
  sys.env.get("MYSQL_TCP_PORT").map(_.toInt),
  sys.env.get("MYSQL_USER").getOrElse(""),
  sys.env.get("MYSQL_PASS").getOrElse(""))))

val storeBuilder = Store.Builder(SpanStoreBuilder(db), AggregatesBuilder(db))

QueryServiceBuilder(storeBuilder)
