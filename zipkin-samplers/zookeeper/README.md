# sampler-zookeeper
This is an adaptive sampler which can help prevent a surge in traffic
from overwhelming the zipkin storage layer.

This works in scenarios where you can coordinate the collection/ingestion
tier and are running zookeeper.

`zipkin.sampler.zookeeper.ZooKeeperSampler.Builder` includes defaults
that will against local Zookeeper.

The sampling implementation is compatible with [zipkin-scala](https://github.com/openzipkin/zipkin/tree/master/zipkin-sampler).
