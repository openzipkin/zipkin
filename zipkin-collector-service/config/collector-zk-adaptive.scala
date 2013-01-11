import com.twitter.zipkin.builder.{ZooKeeperClientBuilder, Scribe}
import com.twitter.zipkin.cassandra
import com.twitter.zipkin.collector.builder.{Adaptive, Adjustable, CollectorServiceBuilder}
import com.twitter.zipkin.storage.Store

val keyspaceBuilder = cassandra.Keyspace.static(nodes = Set("localhost"))
val cassandraBuilder = Store.Builder(
  cassandra.StorageBuilder(keyspaceBuilder),
  cassandra.IndexBuilder(keyspaceBuilder),
  cassandra.AggregatesBuilder(keyspaceBuilder)
)

val zkBuilder = ZooKeeperClientBuilder(Seq("localhost"))
val sampleRate = Adjustable.zookeeper(zkBuilder, "/twitter/service/zipkin/config", "samplerate", 0.1)
val storageRequestRate = Adjustable.zookeeper(zkBuilder, "/twitter/service/zipkin/config", "storagerequestrate", 300000)
val adaptiveSampler = Adaptive.zookeeper(zkBuilder, sampleRate, storageRequestRate)

CollectorServiceBuilder(Scribe())
  .writeTo(cassandraBuilder)
  .sampleRate(sampleRate)
  .addConfigEndpoint("storageRequestRate", storageRequestRate)
  .register(Scribe.serverSets(zkBuilder, Set("/twitter/scribe/zipkin")))

