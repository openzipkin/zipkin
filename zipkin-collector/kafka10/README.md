# collector-kafka10

## KafkaCollector
This collector is implemented as a Kafka consumer supporting Kafka brokers running
version 0.10.0.0 or later. It polls a Kafka topic for messages that contain
a list of spans in json or TBinaryProtocol big-endian encoding. These
spans are pushed to a span consumer.

For information about running this collector as a module in Zipkin server, see
[zipkin-autoconfigure/collector-kafka10](../../zipkin-autoconfigure/collector-kafka10/).

When using this collector as a library outside of Zipkin server,
[zipkin.collector.kafka10.KafkaCollector.Builder](src/main/java/zipkin/collector/kafka10/KafkaCollector.java)
includes defaults that will operate against a Kafka topic name `zipkin`.

## Encoding spans into Kafka messages
The message's binary data includes a list of spans. Supported encodings
are the same as the http [POST /spans](http://zipkin.io/zipkin-api/#/paths/%252Fspans) body.

### Json
The message's binary data is a list of spans in json. The first character must be '[' (decimal 91).

`Codec.JSON.writeSpans(spans)` performs the correct json encoding.

Here's an example, sending a list of a single span to the zipkin topic:

```bash
$ kafka-console-producer.sh --broker-list $ADVERTISED_HOST:9092 --topic zipkin
[{"traceId":"1","name":"bang","id":"2","timestamp":1234,"binaryAnnotations":[{"key":"lc","value":"bamm-bamm","endpoint":{"serviceName":"flintstones","ipv4":"127.0.0.1"}}]}]
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
