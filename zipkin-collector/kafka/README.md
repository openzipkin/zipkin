# collector-kafka

## KafkaCollector
This collector is implemented as a Kafka consumer supporting Kafka brokers running
version 0.10.0.0 or later. It polls a Kafka topic for messages that contain
a list of spans in json or TBinaryProtocol big-endian encoding. These
spans are pushed to a span consumer.

For information about running this collector as a module in Zipkin server, see
the [Zipkin Server README](../../zipkin-server/README.md#kafka-collector).

When using this collector as a library outside of Zipkin server,
[zipkin2.collector.kafka.KafkaCollector.Builder](src/main/java/zipkin2/collector/kafka/KafkaCollector.java)
includes defaults that will operate against a Kafka topic name `zipkin`.

## Encoding spans into Kafka messages
The message's binary data includes a list of spans. Supported encodings
are the same as the http [POST /spans](https://zipkin.io/zipkin-api/#/paths/%252Fspans) body.

### Json
The message's binary data is a list of spans in json. The first character must be '[' (decimal 91).

`Codec.JSON.writeSpans(spans)` performs the correct json encoding.

Here's an example, sending a list of a single span to the zipkin topic:

```bash
$ kafka-console-producer.sh --broker-list $ADVERTISED_HOST:9092 --topic zipkin
[{"traceId":"1","name":"bang","id":"2","timestamp":1470150004071068,"duration":1,"localEndpoint":{"serviceName":"flintstones"},"tags":{"lc":"bamm-bamm"}}]
```

### Thrift
The message's binary data includes a list header followed by N spans serialized in TBinaryProtocol

`Codec.THRIFT.writeSpans(spans)` encodes spans in the following fashion:
```
write_byte(12) // type of the list elements: 12 == struct
write_i32(count) // count of spans that will follow
for (int i = 0; i < count; i++) {
  writeTBinaryProtocol(spans(i))
}
```

### Legacy encoding
Older versions of zipkin accepted a single span per message, as opposed
to a list per message. This practice is deprecated, but still supported.

## Kafka topic settings
By default, this collector creates one instance of KafkaConsumer to poll for messages. Given that,
our suggestion is to create the `zipkin` topic with one partition initially. Observe the KafkaConsumer
instance for considerable consumer lag and scale up the number of instances
using the [KAFKA_STREAMS](../../zipkin-server/README.md#kafka-collector) parameter as needed. For each
KafkaConsumer instance added you should also add a partition to the `zipkin` topic. The number of partitions
should always be equal or larger than the number of KafkaConsumer instances to benefit from the
parallelism an additional KafkaConsumer brings.

Note that tuning this collector should happen in coordination with your storage backend. There is no
point in scaling up the collector if your storage backend is the bottleneck. There are various KafkaConsumer
parameters (`max.poll.records`, `fetch.max.bytes` etc) that allow you to tweak behaviour and prevent
the collector from swamping the storage backend.
