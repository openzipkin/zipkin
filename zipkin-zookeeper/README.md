# sampler-zookeeper
This is an adaptive sampler which can help prevent a surge in traffic
from overwhelming the zipkin storage layer.

This works in scenarios where you can coordinate the collection tier
with ZooKeeper 3.4.x or ZooKeeper 3.5.x.

`zipkin.sampler.zookeeper.ZooKeeperSampler.Builder` includes defaults
that will against a given [Curator](http://curator.apache.org) client.

Example:

```java
// The ZooKeeperSampler requires you to supply your own Curator.
client = CuratorFrameworkFactory.builder()
    .connectString(zookeeperConnect)
    .retryPolicy(new RetryOneTime(1))
    .build();
client.start();
client.blockUntilConnected(3, TimeUnit.SECONDS);

// This will sample spans based on the capacity of the storage layer.
// Capacity is spans/minute and set by an admin.
sampler = new ZooKeeperSampler.Builder()
    .id("kafka-" + topic + "@" + hostAndPort)
    .build(client);

// Initialize a collector that will sample incoming spans based on
// a coordinated rate.
collector = new KafkaCollector.Builder()
    .zookeeper(zookeeperConnect)
    .topic(topic)
    .writeTo(storage, sampler);
```

## ZooKeeper Paths

The following paths are relative to `zipkin.sampler.zookeeper.ZooKeeperSampler.basePath`,
which defaults to "/zipkin/sampler". 

Path | Owner | Description
--- | --- | ---
/targetStoreRate | admin | Admin changes this to no more than 95% capacity of the storage layer in spans/minute.
/sampleRate | leader | Each member of the sample group's `isSampled(Span)` method will pass based on this value. This is updated no more than {sampler.updateInterval} by the leader.
/storeRates/{sampler.id} | each sampler | Updated per {sampler.updateInterval} to the amount of spans sampled per minute.
/election | each sampler | This path is used to determine or elect a new leader.

## More information
More details are available in the [javadoc](src/main/java/zipkin/collector/zookeeper/ZooKeeperCollectorSampler.java)
The sampling implementation was initially based on [zipkin-scala](https://github.com/openzipkin/zipkin/tree/scala/zipkin-sampler).

