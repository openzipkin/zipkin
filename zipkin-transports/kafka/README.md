# transport-kafka
This transport polls a Kafka 8.2.2+ topic for messages that contain
TBinaryProtocol big-endian encoded lists of spans. These spans are
pushed to a span consumer.

`zipkin.kafka.KafkaConfig` includes defaults that will operate against a
Kafka topic advertised in Zookeeper.


## Encoding spans into Kafka messages
`Codec.THRIFT.writeSpans(spans)` encodes spans in the following fashion:

The message's binary data includes a list header followed by N spans serialized in TBinaryProtocol
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