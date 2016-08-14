[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin) [![Build Status](https://travis-ci.org/openzipkin/zipkin.svg?branch=master)](https://travis-ci.org/openzipkin/zipkin) [![Download](https://api.bintray.com/packages/openzipkin/maven/zipkin/images/download.svg) ](https://bintray.com/openzipkin/maven/zipkin/_latestVersion)

# zipkin
[Zipkin](http://zipkin.io) is a distributed tracing system. It helps gather timing data needed to troubleshoot latency problems in microservice architectures. It manages both the collection and lookup of this data. Zipkin’s design is based on the [Google Dapper paper](http://research.google.com/pubs/pub36356.html).

This project includes a dependency-free library and a [spring-boot](http://projects.spring.io/spring-boot/) server. Storage options include in-memory, JDBC (mysql), Cassandra, and Elasticsearch.

## Quick-start

The quickest way to get started is to fetch the [latest released server](https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-server&v=LATEST&c=exec) as a self-contained executable jar. Note that the Zipkin requires minimum JRE 8. For example:

```
wget -O zipkin.jar 'https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-server&v=LATEST&c=exec'
java -jar zipkin.jar
```

You can also start Zipkin via Docker.
```
docker run -d -p 9411:9411 openzipkin/zipkin
```

Once you've started, browse to http://your_host:9411 to find traces!

Check out the [`zipkin-server`](/zipkin-server) documentation for configuration details, or [`docker-zipkin`](https://github.com/openzipkin/docker-zipkin) for how to use docker-compose.

## Core Library
The [core library](https://github.com/openzipkin/zipkin/tree/master/zipkin/src/main/java/io/zipkin) requires minimum language level 7. While currently only used by the server, we expect this library to be used in native instrumentation as well.

This includes built-in codec for both thrift and json structs. Direct dependencies on thrift or moshi (json library) are avoided by minifying and repackaging classes used. The result is a 190k jar which won't conflict with any library you use.

Ex.
```java
// your instrumentation makes a span
archiver = BinaryAnnotation.create(LOCAL_COMPONENT, "archiver", Endpoint.create("service", 127 << 24 | 1));
span = Span.builder()
    .traceId(1L)
    .name("targz")
    .id(1L)
    .timestamp(epochMicros())
    .duration(durationInMicros)
    .addBinaryAnnotation(archiver);

// Now, you can encode it as json or thrift
bytes = Codec.JSON.writeSpan(span);
bytes = Codec.THRIFT.writeSpan(span);
```

## Storage Component
Zipkin includes a [StorageComponent](https://github.com/openzipkin/zipkin/blob/master/zipkin/src/main/java/zipkin/storage/StorageComponent.java), used to store and query spans and dependency links. This is used by the server and those making custom servers, collectors, or span reporters. For this reason, storage components have minimal dependencies; many run on Java 7.

Ex.
```java
// this won't create network connections
storage = CassandraStorage.builder()
                          .contactPoints("my-cassandra-host").build();

// but this will
trace = storage.spanStore().getTrace(traceId);

// clean up any sessions, etc
storage.close();
```

### In-Memory
The [InMemoryStorage](https://github.com/openzipkin/zipkin/blob/master/zipkin/src/main/java/zipkin/storage/InMemoryStorage.java) component is packaged in zipkin's core library. It is not persistent, nor viable for realistic work loads. Its purpose is for testing, for example starting a server on your laptop without any database needed.

### MySQL
The [JDBCStorage](https://github.com/openzipkin/zipkin/tree/master/zipkin-storage/jdbc) component currently is only tested with MySQL 5.6-7. It is designed to be easy to understand, and get started with. For example, it deconstructs spans into columns, so you can perform ad-hoc queries using SQL. However, this component has [known performance issues](https://github.com/openzipkin/zipkin/issues/233): queries will eventually take seconds to return if you put a lot of data into it.

### Cassandra
The [CassandraStorage](https://github.com/openzipkin/zipkin/tree/master/zipkin-storage/cassandra) component is tested against Cassandra 2.2+. It stores spans as opaque thrifts which means you can't read them in cqlsh. However, it is designed for scale. For example, it has manually implemented indexes to make querying larger data more performant. This store requires a [spark job](https://github.com/openzipkin/zipkin-dependencies-spark) to aggregate dependency links.

### Elasticsearch
The [ElasticsearchStorage](https://github.com/openzipkin/zipkin/tree/master/zipkin-storage/elasticsearch) component is tested against Elasticsearch 2.3. It stores spans as json and has been designed for larger scale. This store is the newest option, and does not yet [support dependency links](https://github.com/openzipkin/zipkin-dependencies-spark/issues/21).

## Running the server from source
The [zipkin server](https://github.com/openzipkin/zipkin/tree/master/zipkin-server)
receives spans via HTTP POST and respond to queries from its UI. It can also run collectors, such as Scribe or Kafka.

To run the server from the currently checked out source, enter the following.
```bash
# Build the server and also make its dependencies
$ ./mvnw -DskipTests --also-make -pl zipkin-server clean install
# Run the server
$ java -jar ./zipkin-server/target/zipkin-server-*exec.jar
```

## Artifacts
### Library Releases
Releases are uploaded to [Bintray](https://bintray.com/openzipkin/maven/zipkin).
### Library Snapshots
Snapshots are uploaded to [JFrog](http://oss.jfrog.org/artifactory/oss-snapshot-local) after commits to master.
### Docker Images
Released versions of zipkin-server are published to Docker Hub as `openzipkin/zipkin`.
See [docker-zipkin-java](https://github.com/openzipkin/docker-zipkin-java) for details.
