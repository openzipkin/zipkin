# Kafka 0.8 Collector Auto-configure Module

This module provides support for running the kafa 0.8 collector as a
component of Zipkin server. To activate this collector, reference the
module jar when running the Zipkin server and configure the ZooKeeper
connection string via the `KAFKA_ZOOKEEPER` environment
variable or `zipkin.collector.kafka.zookeeper` property.

## Quick start

JRE 8 is required to run Zipkin server.

Fetch the latest released
[executable jar for Zipkin server](https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-server&v=LATEST&c=exec)
and
[autoconfigure module jar for the kafka collector](https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-autoconfigure-collector-kafka08&v=LATEST&c=module).
Run Zipkin server with the Kafka 0.10+ collector enabled.

For example:

```bash
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s io.zipkin.java:zipkin-autoconfigure-collector-kafka08:LATEST:module kafka08.jar
$ KAFKA_ZOOKEEPER=127.0.0.1:2181 \
    java \
    -Dloader.path='kafka08.jar,kafka08.jar!/lib' \
    -Dspring.profiles.active=kafka08 \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

After executing these steps, the Zipkin UI will be available
[http://localhost:9411](http://localhost:9411) or port 9411 of the remote host the Zipkin server
was started on.

The Zipkin server can be further configured as described in the
[Zipkin server documentation](../../zipkin-server/README.md).

## How this works

The Zipkin server executable jar and the autoconfigure module jar for
the kafka collector are required. The module jar contains the code for
loading and configuring the kafka collector, and any dependencies that
are not already packaged in the Zipkin server jar
(e.g. zipkin-collector-kafka08, kafka-clients).

Using PropertiesLauncher as the main class runs the Zipkin server
executable jar the same as it would be if executed using
`java -jar zipkin.jar`, except it provides the option to load resources
from outside the executable jar into the classpath. Those external
resources are specified using the `loader.path` system property. In this
case, it is configured to load the kafka collector module jar
(`zipkin-autoconfigure-collector-kafka08-module.jar`) and the jar files
contained in the `lib/` directory within that module jar
(`zipkin-autoconfigure-collector-kafka08-module.jar!/lib`).

The `spring.profiles=kafka08` system property causes configuration from
[zipkin-server-kafka08.yml](src/main/resources/zipkin-server-kafka08.yml)
to be loaded.

For more information on how this works, see [Spring Boot's documentation
on the executable jar format](https://docs.spring.io/spring-boot/docs/current/reference/html/executable-jar.html). The
[section on PropertiesLauncher](https://docs.spring.io/spring-boot/docs/current/reference/html/executable-jar.html#executable-jar-property-launcher-features)
has more detail on how the external module jar and the libraries it
contains are loaded.

## Configuration

The following configuration points apply apply when `KAFKA_ZOOKEEPER` or
`zipkin.collector.kafka.zookeeper` is set. They can be configured by
setting an environment variable or by setting a java system property
using the `-Dproperty.name=value` command line argument. Some settings
correspond to "Consumer Configs" in [Kafka 0.8 documentation](https://kafka.apache.org/082/documentation.html#consumerconfigs).

Environment Variable | Property | Consumer Config | Description
--- | --- | --- | ---
`KAFKA_ZOOKEEPER` | `zipkin.collector.kafka.zookeeper` | zookeeper.connect | Comma-separated list of zookeeper host/ports, ex. 127.0.0.1:2181. No default
`KAFKA_GROUP_ID` | `zipkin.collector.kafka.group-id` | group.id | The consumer group this process is consuming on behalf of. Defaults to `zipkin`
`KAFKA_TOPIC` | `zipkin.collector.kafka.topic` | N/A | The topic that zipkin spans will be consumed from. Defaults to `zipkin`
`KAFKA_STREAMS` | `zipkin.collector.kafka.streams` | N/A | Count of threads consuming the topic. Defaults to `1`

### Other Kafka consumer properties
You may need to set other [Kafka consumer properties](https://kafka.apache.org/082/documentation.html#consumerconfigs), in
addition to the ones with explicit properties defined by the collector.
In this case, you need to prefix that property name with
`zipkin.collector.kafka.overrides` and pass it as a system property argument.

For example, to override `auto.offset.reset`, you can set a system property named
`zipkin.collector.kafka.overrides.auto.offset.reset`:

```bash
$ KAFKA_ZOOKEEPER=127.0.0.1:2181 \
    java \
    -Dloader.path='kafka08.jar,kafka08.jar!/lib' \
    -Dspring.profiles.active=kafka08 \
    -Dzipkin.collector.kafka.overrides.auto.offset.reset=latest \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

### Examples

Multiple ZooKeeper servers:

```bash
$ KAFKA_ZOOKEEPER=zk1:2181,zk2:2181 \
    java \
    -Dloader.path='kafka08.jar,kafka08.jar!/lib' \
    -Dspring.profiles.active=kafka08 \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

Alternate topic name(s):

```bash
$ KAFKA_ZOOKEEPER=127.0.0.1:2181 \
    java \
    -Dloader.path='kafka08.jar,kafka08.jar!/lib' \
    -Dspring.profiles.active=kafka08 \
    -Dzipkin.collector.kafka.topic=zapkin,zipken \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

Specifying ZooKeeper as a system property, instead of an environment variable:

```bash
$ java \
    -Dloader.path='kafka08.jar,kafka08.jar!/lib' \
    -Dspring.profiles.active=kafka08 \
    -Dzipkin.collector.kafka.zookeeper=127.0.0.1:2181 \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```
