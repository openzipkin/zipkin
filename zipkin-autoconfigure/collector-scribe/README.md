# Scribe Collector Auto-configure Module

This module provides support for running the legacy Scribe collector as
a component of Zipkin server. To activate this collector, reference the
module jar when running the Zipkin server and configure one or more
bootstrap brokers via the `SCRIBE_ENABLED=true` environment variable or
the property `zipkin.collector.scribe.enabled=true`.

## Quick start

JRE 8 is required to run Zipkin server.

Fetch the latest released
[executable jar for Zipkin server](https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-server&v=LATEST&c=exec)
and
[autoconfigure module jar for the scribe collector](https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-autoconfigure-collector-scribe&v=LATEST&c=module).
Run Zipkin server with the Scribe collector enabled.

For example:

```bash
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s io.zipkin.java:zipkin-autoconfigure-collector-scribe:LATEST:module scribe.jar
$ SCRIBE_ENABLED=true \
    java \
    -Dloader.path='scribe.jar,scribe.jar!/lib' \
    -Dspring.profiles.active=scribe \
    -cp zipkin.jar \
    org.springframework.boot.loader.PropertiesLauncher
```

After executing these steps, the Zipkin UI will be available
[http://localhost:9411](http://localhost:9411) or port 9411 of the remote
host the Zipkin server was started on. Scribe will be listening on port
9410.

The Zipkin server can be further configured as described in the
[Zipkin server documentation](../../zipkin-server/README.md).

## How this works

The Zipkin server executable jar and the autoconfigure module jar for the
scribe collector are required. The module jar contains the code for
loading and configuring the scribe collector, and any dependencies that
are not already packaged in the Zipkin server jar.

Using PropertiesLauncher as the main class runs the Zipkin server
executable jar the same as it would be if executed using `java -jar zipkin.jar`,
except it provides the option to load resources from outside the
executable jar into the classpath. Those external resources are specified
using the `loader.path` system property. In this case, it is configured
to load the scribe collector module jar (`zipkin-autoconfigure-collector-scribe-module.jar`)
and the jar files contained in the `lib/` directory within that module
jar (`zipkin-autoconfigure-collector-scribe-module.jar!/lib`).

The `spring.profiles=scribe` system property causes configuration from
[zipkin-server-scribe.yml](src/main/resources/zipkin-server-scribe.yml)
to be loaded.

For more information on how this works, see [Spring Boot's documentation on the executable jar
format](https://docs.spring.io/spring-boot/docs/current/reference/html/executable-jar.html). The
[section on PropertiesLauncher](https://docs.spring.io/spring-boot/docs/current/reference/html/executable-jar.html#executable-jar-property-launcher-features)
has more detail on how the external module jar and the libraries it contains are loaded.

## Configuration

The following configuration points apply apply when `SCRIBE_ENABLED=true`
environment variable or the property `zipkin.collector.scribe.enabled=true`.
They can be configured by setting an environment variable or by setting
a java system property using the `-Dproperty.name=value` command line
argument.

Environment Variable | Property |Description
--- | --- | --- | ---
`COLLECTOR_PORT` | `zipkin.collector.scribe.port` | The port to listen for thrift RPC scribe requests. Defaults to 9410
`SCRIBE_CATEGORY` | `zipkin.collector.scribe.category` | Category zipkin spans will be consumed from. Defaults to `zipkin`
