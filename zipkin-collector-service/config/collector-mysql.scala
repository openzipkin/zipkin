import com.twitter.zipkin.anormdb.{DependencyStoreBuilder, SpanStoreBuilder}
import com.twitter.zipkin.collector.builder.CollectorServiceBuilder
import com.twitter.zipkin.receiver.kafka.KafkaSpanReceiverFactory
import com.twitter.zipkin.storage.Store
import com.twitter.zipkin.storage.anormdb.{DB, DBConfig, DBParams}

val db = DB(DBConfig("mysql", new DBParams(
  dbName = "zipkin",
  sys.env.get("MYSQL_HOST").getOrElse("localhost"),
  sys.env.get("MYSQL_TCP_PORT").map(_.toInt),
  sys.env.get("MYSQL_USER").getOrElse(""),
  sys.env.get("MYSQL_PASS").getOrElse(""))))

val storeBuilder = Store.Builder(SpanStoreBuilder(db, true), DependencyStoreBuilder(db))
val kafkaReceiver = sys.env.get("KAFKA_ZOOKEEPER").map(
  KafkaSpanReceiverFactory.factory(_, sys.env.get("KAFKA_TOPIC").getOrElse("zipkin"))
)

CollectorServiceBuilder(storeBuilder, kafkaReceiver)
