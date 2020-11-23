# zipkin

[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin)
[![Build Status](https://github.com/openzipkin/zipkin/workflows/test/badge.svg)](https://github.com/openzipkin/zipkin/actions?query=workflow%3Atest)
[![Maven Central](https://img.shields.io/maven-central/v/io.zipkin/zipkin-server.svg)](https://search.maven.org/search?q=g:io.zipkin%20AND%20a:zipkin-server)

[Zipkin](https://zipkin.io) is a distributed tracing system. It helps gather
timing data needed to troubleshoot latency problems in service architectures.
Features include both the collection and lookup of this data.

If you have a trace ID in a log file, you can jump directly to it. Otherwise,
you can query based on attributes such as service, operation name, tags and
duration. Some interesting data will be summarized for you, such as the
percentage of time spent in a service, and whether or not operations failed.

<img src="https://zipkin.io/public/img/web-screenshot.png" alt="Trace view screenshot" />

The Zipkin UI also presents a dependency diagram showing how many traced
requests went through each application. This can be helpful for identifying
aggregate behavior including error paths or calls to deprecated services.

<img src="https://zipkin.io/public/img/dependency-graph.png" alt="Dependency graph screenshot" />

Application’s need to be “instrumented” to report trace data to Zipkin. This
usually means configuration of a [tracer or instrumentation library](https://zipkin.io/pages/tracers_instrumentation.html). The most
popular ways to report data to Zipkin are via http or Kafka, though many other
options exist, such as Apache ActiveMQ, gRPC and RabbitMQ. The data served to
the UI is stored in-memory, or persistently with a supported backend such as
Apache Cassandra or Elasticsearch.

## Quick-start

The quickest way to get started is to fetch the [latest released server](https://search.maven.org/remote_content?g=io.zipkin&a=zipkin-server&v=LATEST&c=exec) as a self-contained executable jar. Note that the Zipkin server requires minimum JRE 8. For example:

```bash
curl -sSL https://zipkin.io/quickstart.sh | bash -s
java -jar zipkin.jar
```

You can also start Zipkin via Docker.
```bash
# Note: this is mirrored as ghcr.io/openzipkin/zipkin
docker run -d -p 9411:9411 openzipkin/zipkin
```

Once the server is running, you can view traces with the Zipkin UI at `http://your_host:9411/zipkin/`.

If your applications aren't sending traces, yet, configure them with [Zipkin instrumentation](https://zipkin.io/pages/tracers_instrumentation) or try one of our [examples](https://github.com/openzipkin?utf8=%E2%9C%93&q=example).

Check out the [`zipkin-server`](/zipkin-server) documentation for configuration details, or [Docker examples](docker/examples) for how to use docker-compose.

### Zipkin Slim

The slim build of Zipkin is smaller and starts faster. It supports in-memory and Elasticsearch storage, but doesn't support messaging transports like Kafka or RabbitMQ. If these constraints match your needs, you can try slim like below:

Running via Java:
```bash
curl -sSL https://zipkin.io/quickstart.sh | bash -s io.zipkin:zipkin-server:LATEST:slim zipkin.jar
java -jar zipkin.jar
```

Running via Docker:
```bash
# Note: this is mirrored as ghcr.io/openzipkin/zipkin-slim
docker run -d -p 9411:9411 openzipkin/zipkin-slim
```

## Core Library
The [core library](zipkin/src/main/java/zipkin2) is used by both Zipkin instrumentation and the Zipkin server. Its minimum Java language level is 6, in efforts to support those writing agent instrumentation.

This includes built-in codec for Zipkin's v1 and v2 json formats. A direct dependency on gson (json library) is avoided by minifying and repackaging classes used. The result is a 155k jar which won't conflict with any library you use.

Ex.
```java
// All data are recorded against the same endpoint, associated with your service graph
localEndpoint = Endpoint.newBuilder().serviceName("tweetie").ip("192.168.0.1").build()
span = Span.newBuilder()
    .traceId("d3d200866a77cc59")
    .id("d3d200866a77cc59")
    .name("targz")
    .localEndpoint(localEndpoint)
    .timestamp(epochMicros())
    .duration(durationInMicros)
    .putTag("compression.level", "9");

// Now, you can encode it as json
bytes = SpanBytesEncoder.JSON_V2.encode(span);
```

Note: The above is just an example, most likely you'll want to use an existing tracing library like [Brave](https://github.com/openzipkin/brave)

## Storage Component
Zipkin includes a [StorageComponent](zipkin/src/main/java/zipkin2/storage/StorageComponent.java), used to store and query spans and
dependency links. This is used by the server and those making collectors, or span reporters. For this reason, storage
components have minimal dependencies, but most require Java 8+

Ex.
```java
// this won't create network connections
storage = ElasticsearchStorage.newBuilder()
                              .hosts(asList("http://myelastic:9200")).build();

// prepare a call
traceCall = storage.spanStore().getTrace("d3d200866a77cc59");

// execute it synchronously or asynchronously
trace = traceCall.execute();

// clean up any sessions, etc
storage.close();
```

### In-Memory
The [InMemoryStorage](zipkin-server#in-memory-storage) component is packaged in zipkin's core library. It
is neither persistent, nor viable for realistic work loads. Its purpose
is for testing, for example starting a server on your laptop without any
database needed.

### Cassandra
The [Cassandra](zipkin-server#cassandra-storage) component uses Cassandra
3.11.3+ features, but is tested against the latest patch of Cassandra 3.11.

This is the second generation of our Cassandra schema. It stores spans
using UDTs, such that they appear like Zipkin v2 json in cqlsh. It is
designed for scale, and uses a combination of SASI and manually
implemented indexes to make querying larger data more performant.

Note: This store requires a [job to aggregate](https://github.com/openzipkin/zipkin-dependencies) dependency links.

### Elasticsearch
The [Elasticsearch](zipkin-server#elasticsearch-storage) component uses
Elasticsearch 5+ features, but is tested against Elasticsearch 6-7.x.

It stores spans as Zipkin v2 json so that integration with other tools is
straightforward. To help with scale, this uses a combination of custom
and manually implemented indexing.

Note: This store requires a [spark job](https://github.com/openzipkin/zipkin-dependencies) to aggregate dependency links.

### Disabling search
The following API endpoints provide search features, and are enabled by
default. Search primarily allows the trace list screen of the UI operate.
* `GET /services` - Distinct Span.localServiceName
* `GET /remoteServices?serviceName=X` - Distinct Span.remoteServiceName by Span.localServiceName
* `GET /spans?serviceName=X` - Distinct Span.name by Span.localServiceName
* `GET /autocompleteKeys` - Distinct keys of Span.tags subject to configurable whitelist
* `GET /autocompleteValues?key=X` - Distinct values of Span.tags by key
* `GET /traces` - Traces matching a query possibly including the above criteria


When search is disabled, traces can only be retrieved by ID
(`GET /trace/{traceId}`). Disabling search is only viable when there is
an alternative way to find trace IDs, such as logs. Disabling search can
reduce storage costs or increase write throughput.

`StorageComponent.Builder.searchEnabled(false)` is implied when a zipkin
is run with the env variable `SEARCH_ENABLED=false`.

### Legacy (v1) components
The following components are no longer encouraged, but exist to help aid
transition to supported ones. These are indicated as "v1" as they use
data layouts based on Zipkin's V1 Thrift model, as opposed to the
simpler v2 data model currently used.

#### MySQL
The [MySQL v1](zipkin-storage/mysql-v1) component uses MySQL 5.6+
features, but is tested against MariaDB 10.3.

The schema was designed to be easy to understand and get started with;
it was not designed for performance. Ex spans fields are columns, so
you can perform ad-hoc queries using SQL. However, this component has
[known performance issues](https://github.com/openzipkin/zipkin/issues/1233): queries will eventually take seconds to return
if you put a lot of data into it.

This store does not require a [job to aggregate](https://github.com/openzipkin/zipkin-dependencies) dependency links.
However, running the job will improve performance of dependencies
queries.

## Running the server from source
The [Zipkin server](zipkin-server) receives spans via HTTP POST and respond to queries
from its UI. It can also run collectors, such as RabbitMQ or Kafka.

To run the server from the currently checked out source, enter the
following. JDK 11 is required to compile the source.
```bash
# Build the server and also make its dependencies
$ ./mvnw -q --batch-mode -DskipTests --also-make -pl zipkin-server clean install
# Run the server
$ java -jar ./zipkin-server/target/zipkin-server-*exec.jar
```

## Artifacts
Server artifacts are under the maven group id `io.zipkin`
Library artifacts are under the maven group id `io.zipkin.zipkin2`

### Library Releases
Releases are at [Sonatype](https://oss.sonatype.org/content/repositories/releases) and [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.zipkin%22)

### Library Snapshots
Snapshots are uploaded to [Sonatype](https://oss.sonatype.org/content/repositories/snapshots) after
commits to master.

### Docker Images
Released versions of zipkin-server are published to Docker Hub as `openzipkin/zipkin` and GitHub
Container Registry as `ghcr.io/openzipkin/zipkin`. See [docker](./docker) for details.

### Javadocs
https://zipkin.io/zipkin contains versioned folders with JavaDocs published on each (non-PR) build, as well
as releases.
