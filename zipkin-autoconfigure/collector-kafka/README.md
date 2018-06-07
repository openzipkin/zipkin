# Kafka 0.10+ Collector Auto-configure Module

This module provides support for running the kafak10 collector as a
component of Zipkin server via the `KAFKA_BOOTSTRAP_SERVERS` environment
variable or `zipkin.collector.kafka.bootstrap-servers` property.

## Configuration

The following configuration points apply apply when `KAFKA_BOOTSTRAP_SERVERS` or
`zipkin.collector.kafka.bootstrap-servers` is set. They can be configured by setting an environment
variable or by setting a java system property using the `-Dproperty.name=value` command line
argument. Some settings correspond to "New Consumer Configs" in
[Kafka documentation](https://kafka.apache.org/documentation/#newconsumerconfigs).

Environment Variable | Property | New Consumer Config | Description
--- | --- | --- | ---
`KAFKA_BOOTSTRAP_SERVERS` | `zipkin.collector.kafka.bootstrap-servers` | bootstrap.servers | Comma-separated list of brokers, ex. 127.0.0.1:9092. No default
`KAFKA_GROUP_ID` | `zipkin.collector.kafka.group-id` | group.id | The consumer group this process is consuming on behalf of. Defaults to `zipkin`
`KAFKA_TOPIC` | `zipkin.collector.kafka.topic` | N/A | Comma-separated list of topics that zipkin spans will be consumed from. Defaults to `zipkin`
`KAFKA_STREAMS` | `zipkin.collector.kafka.streams` | N/A | Count of threads consuming the topic. Defaults to `1`

### Other Kafka consumer properties
You may need to set other
[Kafka consumer properties](https://kafka.apache.org/documentation/#newconsumerconfigs), in
addition to the ones with explicit properties defined by the collector. In this case, you need to
prefix that property name with `zipkin.collector.kafka.overrides` and pass it as a system property
argument.

For example, to override `auto.offset.reset`, you can set a system property named
`zipkin.collector.kafka.overrides.auto.offset.reset`:

```bash
$ KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 java -jar zipkin.jar
```

### Examples

Multiple bootstrap servers:

```bash
$ KAFKA_BOOTSTRAP_SERVERS=broker1.local:9092,broker2.local:9092 java -jar zipkin.jar
```

Alternate topic name(s):

```bash
$ KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 \
    java -Dzipkin.collector.kafka.topic=zapkin,zipken -jar zipkin.jar
```

Specifying bootstrap servers as a system property, instead of an environment variable:

```bash
$ java -Dzipkin.collector.kafka.bootstrap-servers=127.0.0.1:9092 -jar zipkin.jar
```

### Migration from Kafka < 0.8.1

As explained [on kafka wiki](https://cwiki.apache.org/co\
nfluence/display/KAFKA/Committing+and+fetching+consumer+offsets+in+Kafka), offsets were stored in ZooKeeper. This has changed and offsets are now stored directly in Kafka. You need to update offsets in Kafka 0.10 by following the instructions.
