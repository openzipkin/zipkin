# collector-pulsar

## PulsarCollector

This collector is implemented as a Pulsar consumer supporting Pulsar brokers running
version 4.x or later, and the default subscription type is `Shared`, in Shared subscription type, 
multiple consumers can attach to the same subscription and messages are delivered 
in a round-robin distribution across consumers.

This collector is implemented as a Pulsar consumer supporting Pulsar brokers running version 4.x or later. 
The default `subscriptionType` is `Shared`, which allows multiple consumers to attach to the same subscription, 
with messages delivered in a round-robin distribution across consumers, the default `subscriptionInitialPosition`
is `Earliest`, you can modify the consumer settings as needed through the `consumerProps` parameter.
Also, the client settings can also be modified through the `clientProps` parameter.

For information about running this collector as a module in Zipkin server, see
the [Zipkin Server README](../../zipkin-server/README.md#pulsar-collector).

When using this collector as a library outside of Zipkin server,
[zipkin2.collector.pulsar.PulsarCollector.Builder](src/main/java/zipkin2/collector/pulsar/PulsarCollector.java)
includes defaults that will operate against a Pulsar topic name `zipkin`.

## Encoding spans into Pulsar messages

The message's binary data includes a list of spans. Supported encodings
are the same as the http [POST /spans](https://zipkin.io/zipkin-api/#/paths/%252Fspans) body.

### Json

The message's binary data is a list of spans in json. The first character must be '[' (decimal 91).

`Codec.JSON.writeSpans(spans)` performs the correct json encoding.

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

## Logging

Zipkin by default suppresses all logging output from Pulsar client operations as they can get quite verbose. Start
Zipkin
with `--logging.level.org.apache.pulsar=INFO` or similar to override this during troubleshooting for example.
