# Kafka 0.10+ Collector Auto-configure Module

This module provides support for running the kafak10 collector as a component of Zipkin server. To
activate this collector, reference the module jar when running the Zipkin server
and configure one or more bootstrap brokers via the `KAFKA_BOOTSTRAP_SERVERS` environment
variable or `zipkin.collector.kafka.bootstrap-servers` property.

## Quick start

JRE 8 is required to run Zipkin server. Note: The Kafka 0.10+ collector and this auto-configure
module are compatible with Java 7 and later when used independent of Zipkin server.

Fetch the latest released
[executable jar for Zipkin server](https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-server&v=LATEST&c=exec)
and
[autoconfigure module jar for the kafka10 collector](https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-autoconfigure-collector-kafka10&v=LATEST&c=module).
Run Zipkin server with the Kafka 0.10+ collector enabled.

For example:

```bash
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s io.zipkin.java:zipkin-autoconfigure-collector-kafka10:LATEST:module kafka10.jar
$ KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 \
    java \
    -Dloader.path='kafka10.jar,kafka10.jar!/lib' \
    -Dspring.profiles.active=kafka \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

After executing these steps, the Zipkin UI will be available
[http://localhost:9411](http://localhost:9411) or port 9411 of the remote host the Zipkin server
was started on.

The Zipkin server can be further configured as described in the
[Zipkin server documentation](../../zipkin-server/README.md).

## How this works

The Zipkin server executable jar and the autoconfigure module jar for the kafka10 collector are
required. The module jar contains the code for loading and configuring the kafka10 collector, and
any dependencies that are not already packaged in the Zipkin server jar (e.g.
zipkin-collector-kafka10, kafka-clients).

Using PropertiesLauncher as the main class runs the Zipkin server executable jar the same as it
would be if executed using `java -jar zipkin.jar`, except it provides the option to
load resources from outside the executable jar into the classpath. Those external resources are
specified using the `loader.path` system property. In this case, it is configured to load the
kafka10 collector module jar (`zipkin-autoconfigure-collector-kafka10-module.jar`) and the jar files
contained in the `lib/` directory within that module jar
(`zipkin-autoconfigure-collector-kafka10-module.jar!/lib`).

The `spring.profiles=kafka` system property causes configuration from
[zipkin-server-kafka.yml](src/main/resources/zipkin-server-kafka.yml) to be loaded.

For more information on how this works, see [Spring Boot's documentation on the executable jar
format](https://docs.spring.io/spring-boot/docs/current/reference/html/executable-jar.html). The
[section on PropertiesLauncher](https://docs.spring.io/spring-boot/docs/current/reference/html/executable-jar.html#executable-jar-property-launcher-features)
has more detail on how the external module jar and the libraries it contains are loaded.

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
$ KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 \
    java \
    -Dloader.path='kafka10.jar,kafka10.jar!/lib' \
    -Dspring.profiles.active=kafka \
    -Dzipkin.collector.kafka.overrides.auto.offset.reset=latest \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

### Examples

Multiple bootstrap servers:

```bash
$ KAFKA_BOOTSTRAP_SERVERS=broker1:9092.local,broker2.local:9092 \
    java \
    -Dloader.path='kafka10.jar,kafka10.jar!/lib' \
    -Dspring.profiles.active=kafka \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

Alternate topic name(s):

```bash
$ KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 \
    java \
    -Dloader.path='kafka10.jar,kafka10.jar!/lib' \
    -Dspring.profiles.active=kafka \
    -Dzipkin.collector.kafka.topic=zapkin,zipken \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

Specifying bootstrap servers as a system property, instead of an environment variable:

```bash
$ java \
    -Dloader.path='kafka10.jar,kafka10.jar!/lib' \
    -Dspring.profiles.active=kafka \
    -Dzipkin.collector.kafka.bootstrap-servers=127.0.0.1:9092 \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

### Migration from Kafka < 0.8.1

As explained [on kafka wiki](https://cwiki.apache.org/co\
nfluence/display/KAFKA/Committing+and+fetching+consumer+offsets+in+Kafka), offsets were stored in ZooKeeper. This has changed and offsets are now stored directly in Kafka. You need to update offsets in Kafka 0.10 by following the instructions.
