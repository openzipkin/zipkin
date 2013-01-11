import com.twitter.zipkin.builder.{Scribe, ZooKeeperClientBuilder}
import com.twitter.zipkin.cassandra
import com.twitter.zipkin.collector.builder.{Adjustable, CollectorServiceBuilder}
import com.twitter.zipkin.config.sampler.ZooKeeperAdjustableRateConfig
import com.twitter.zipkin.storage.Store

val keyspaceBuilder = cassandra.Keyspace.static(nodes = Set("localhost"))
val cassandraBuilder = Store.Builder(
  cassandra.StorageBuilder(keyspaceBuilder),
  cassandra.IndexBuilder(keyspaceBuilder),
  cassandra.AggregatesBuilder(keyspaceBuilder)
)

val zkBuilder = ZooKeeperClientBuilder(Seq("localhost"))
val sampleRateConfig = new ZooKeeperAdjustableRateConfig()

CollectorServiceBuilder(Scribe.Interface())
  .writeTo(cassandraBuilder)
  .sampleRate(Adjustable.zookeeper(zkBuilder, "/twitter/service/zipkin/config", "samplerate", 0.1))
  .use(ServerSets())
