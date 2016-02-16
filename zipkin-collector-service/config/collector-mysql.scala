import com.google.common.util.concurrent.Atomics
import com.twitter.zipkin.anormdb.{DependencyStoreBuilder, SpanStoreBuilder}
import com.twitter.zipkin.collector.builder.{CollectorServiceBuilder, ZipkinServerBuilder}
import com.twitter.zipkin.receiver.kafka.KafkaSpanReceiverFactory
import com.twitter.zipkin.storage.Store
import com.twitter.zipkin.storage.anormdb.{DB, DBConfig, DBParams}

val serverPort = sys.env.get("COLLECTOR_PORT").getOrElse("9410").toInt
val adminPort = sys.env.get("COLLECTOR_ADMIN_PORT").getOrElse("9900").toInt
val logLevel = sys.env.get("COLLECTOR_LOG_LEVEL").getOrElse("INFO")
val sampleRate = sys.env.get("COLLECTOR_SAMPLE_RATE").getOrElse("1.0").toFloat

val db = DB(DBConfig(
  name = "mysql",
  params = new DBParams(
    sys.env.get("MYSQL_DB").getOrElse("zipkin"),
    sys.env.get("MYSQL_HOST").getOrElse("localhost"),
    sys.env.get("MYSQL_TCP_PORT").map(_.toInt),
    sys.env.get("MYSQL_USER").getOrElse(""),
    sys.env.get("MYSQL_PASS").getOrElse(""),
    sys.env.get("MYSQL_USE_SSL").map(_.toBoolean).getOrElse(false)
  ),
  maxConnections = sys.env.get("MYSQL_MAX_CONNECTIONS").map(_.toInt).getOrElse(10)
))

// Note: schema must be present prior to starting the collector or query
//   - zipkin-anormdb/src/main/resources/mysql.sql
val storeBuilder = Store.Builder(SpanStoreBuilder(db), DependencyStoreBuilder(db))
val kafkaReceiver = sys.env.get("KAFKA_ZOOKEEPER").map(
  KafkaSpanReceiverFactory.factory(_,
    sys.env.get("KAFKA_TOPIC").getOrElse("zipkin"),
    sys.env.get("KAFKA_GROUP_ID").getOrElse("zipkin"),
    sys.env.get("KAFKA_STREAMS").getOrElse("1").toInt
  )
)

CollectorServiceBuilder(
  storeBuilder,
  kafkaReceiver,
  serverBuilder = ZipkinServerBuilder(serverPort, adminPort),
  sampleRate = Atomics.newReference(sampleRate),
  logLevel = logLevel
)
