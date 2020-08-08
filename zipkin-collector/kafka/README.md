# collector-kafka

## KafkaCollector
This collector is implemented as a Kafka consumer supporting Kafka brokers running
version 0.10.0.0 or later. It polls a Kafka [topic](#kafka-configuration) for messages that contain
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

## Kafka configuration

Below are a few guidelines for the Kafka infrastructure used by this collector:
* The collector does not explicitly create the `zipkin` topic itself. If your cluster has auto topic creation enabled then it will be created by Kafka automatically using the broker configured defaults. We recommend therefor creating the topic manually before starting the collector, using configuration parameters adapted for your Zipkin setup.
* The collector will not fail if the `zipkin` topic does not exist, it will instead just wait for the topic to become available.
* A size based retention makes more sense than the default time based (1 week), to safeguard against large bursts of span data.
* The collector starts 1 instance of `KafkaConsumer` by default. We do recommend creating the `zipkin` topic with 6 or more partitions however, as it allows you to easily scale out the collector later by increasing the [KAFKA_STREAMS](../../zipkin-server/README.md#kafka-collector) parameter.
* As Zipkin reporter sends batches of spans which do not rely on any kind of ordering guarantee (key=null), you can increase the number of partitions without affecting ordering. It does not make sense however to have more `KafkaConsumer` instances than partitions as the instances will just be idle and not consume anything.
* Monitoring the consumer lag of the collector as well as the size of the topic will help you to decide if scaling up or down is needed.
* Tuning this collector should happen in coordination with the storage backend. Parameters like `max.poll.records`, `fetch.max.bytes` can prevent the collector from overloading the storage backend, or if it's sized properly they could instead be used to increase ingestion rate.
* A large and consistent consumer lag can indicate that the storage has difficulties with the ingestion rate and could be scaled up.

## Logging
Zipkin by default suppresses all logging output from Kafka client operations as they can get quite verbose. Start Zipkin with `--logging.level.org.apache.kafka=INFO` or similar to override this during troubleshooting for example.
