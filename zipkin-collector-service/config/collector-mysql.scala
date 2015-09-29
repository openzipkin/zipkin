import com.twitter.zipkin.anormdb.{DependencyStoreBuilder, SpanStoreBuilder}
import com.twitter.zipkin.collector.builder.{Adjustable, CollectorServiceBuilder, ZipkinServerBuilder}
import com.twitter.zipkin.receiver.kafka.KafkaSpanReceiverFactory
import com.twitter.zipkin.storage.Store
import com.twitter.zipkin.storage.anormdb.{DB, DBConfig, DBParams}

val serverPort = sys.env.get("COLLECTOR_PORT").getOrElse("9410").toInt
val adminPort = sys.env.get("COLLECTOR_ADMIN_PORT").getOrElse("9900").toInt
val logLevel = sys.env.get("COLLECTOR_LOG_LEVEL").getOrElse("INFO")
val sampleRate = sys.env.get("COLLECTOR_SAMPLE_RATE").getOrElse("1.0").toDouble

val db = DB(DBConfig("mysql", new DBParams(
  dbName = "zipkin",
  sys.env.get("MYSQL_HOST").getOrElse("localhost"),
  sys.env.get("MYSQL_TCP_PORT").map(_.toInt),
  sys.env.get("MYSQL_USER").getOrElse(""),
  sys.env.get("MYSQL_PASS").getOrElse(""))))

// Note: schema must be present prior to starting the collector or query
//   - zipkin-anormdb/src/main/resources/mysql.sql
val storeBuilder = Store.Builder(SpanStoreBuilder(db), DependencyStoreBuilder(db))
val kafkaReceiver = sys.env.get("KAFKA_ZOOKEEPER").map(
  KafkaSpanReceiverFactory.factory(_, sys.env.get("KAFKA_TOPIC").getOrElse("zipkin"))
)

CollectorServiceBuilder(
  storeBuilder,
  kafkaReceiver,
  serverBuilder = ZipkinServerBuilder(serverPort, adminPort),
  logLevel = logLevel
).sampleRate(Adjustable.local(sampleRate))
