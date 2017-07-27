[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin) [![Build Status](https://travis-ci.org/openzipkin/zipkin.svg?branch=master)](https://travis-ci.org/openzipkin/zipkin) [![Download](https://api.bintray.com/packages/openzipkin/maven/zipkin/images/download.svg) ](https://bintray.com/openzipkin/maven/zipkin/_latestVersion)

##CHANGES FROM ORIGINAL REPO

It forwards all spans to a kafka topic for processing. 
You should start it for example: 

SCRIBE_ENABLED=true KAFKA_BROKER_OUTPUT=kafka.marathon.mesos:9042 KAFKA_TOPIC_OUTPUT=zipkin-spans STORAGE_TYPE=elasticsearch ES_HOSTS=http://10.0.0.117:9200,http://10.0.0.118:9200 java -jar zipkin-server.jar

# zipkin
[Zipkin](http://zipkin.io) is a distributed tracing system. It helps gather timing data needed to troubleshoot latency problems in microservice architectures. It manages both the collection and lookup of this data. Zipkin’s design is based on the [Google Dapper paper](http://research.google.com/pubs/pub36356.html).

This project includes a dependency-free library and a [spring-boot](http://projects.spring.io/spring-boot/) server. Storage options include in-memory, JDBC (mysql), Cassandra, and Elasticsearch.

## Quick-start

The quickest way to get started is to fetch the [latest released server](https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-server&v=LATEST&c=exec) as a self-contained executable jar. Note that the Zipkin server requires minimum JRE 8. For example:

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
The [core library](zipkin/src/main/java/zipkin) is used by both Zipkin instrumentation and the Zipkin server. Its minimum Java language level is 6, in efforts to support those writing agent instrumentation.

This includes built-in codec for both thrift and json structs. A direct dependency on gson (json library) is avoided by minifying and repackaging classes used. The result is a 155k jar which won't conflict with any library you use.

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
Zipkin includes a [StorageComponent](zipkin/src/main/java/zipkin/storage/StorageComponent.java), used to store and query spans and dependency links. This is used by the server and those making custom servers, collectors, or span reporters. For this reason, storage components have minimal dependencies; many run on Java 7.

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
The [InMemoryStorage](zipkin/src/main/java/zipkin/storage/InMemoryStorage.java) component is packaged in zipkin's core library. It is not persistent, nor viable for realistic work loads. Its purpose is for testing, for example starting a server on your laptop without any database needed.

### MySQL
The [MySQLStorage](zipkin-storage/mysql) component currently is only tested with MySQL 5.6-7. It is designed to be easy to understand, and get started with. For example, it deconstructs spans into columns, so you can perform ad-hoc queries using SQL. However, this component has [known performance issues](https://github.com/openzipkin/zipkin/issues/1233): queries will eventually take seconds to return if you put a lot of data into it.

### Cassandra
The [CassandraStorage](zipkin-storage/cassandra) component is tested against Cassandra 2.2+. It stores spans as opaque thrifts which means you can't read them in cqlsh. However, it is designed for scale. For example, it has manually implemented indexes to make querying larger data more performant. This store requires a [spark job](https://github.com/openzipkin/zipkin-dependencies) to aggregate dependency links.

### Elasticsearch
The [ElasticsearchHttpStorage](zipkin-storage/elasticsearch-http) component is tested against Elasticsearch 2.x and 5.x. It stores spans as json and has been designed for larger scale. This store requires a [spark job](https://github.com/openzipkin/zipkin-dependencies) to aggregate dependency links.

## Running the server from source
The [zipkin server](zipkin-server)
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
See [docker-zipkin](https://github.com/openzipkin/docker-zipkin) for details.
### Javadocs
http://zipkin.io/zipkin contains versioned folders with JavaDocs published on each (non-PR) build, as well
as releases.
