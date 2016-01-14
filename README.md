[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin) [![Build Status](https://travis-ci.org/openzipkin/zipkin-java.svg?branch=master)](https://travis-ci.org/openzipkin/zipkin-java) [![Download](https://api.bintray.com/packages/openzipkin/maven/zipkin-java/images/download.svg) ](https://bintray.com/openzipkin/maven/zipkin-java/_latestVersion)

# zipkin-java
This project is a native java port of [zipkin](https://github.com/openzipkin/zipkin), which was historically written in scala+finagle. This includes a dependency-free library and a [spring-boot](http://projects.spring.io/spring-boot/) replacement for zipkin's query server. Storage options are currently limited to in-memory and JDBC (mysql), but Cassandra support is expected soon.

## Library
The [core library](https://github.com/openzipkin/zipkin-java/tree/master/zipkin-java-core/src/main/java/io/zipkin) requires minimum language level 7. While currently only used by the server, we expect this library to be used in native instrumentation as well.

This includes built-in codec for both thrift and json structs. Direct dependencies on thrift or moshi (json library) are avoided by minifying and repackaging classes used. The result is a 256k jar which won't conflict with any library you use.

Ex.
```java
// your instrumentation makes a span
archiver = BinaryAnnotation.create(LOCAL_COMPONENT, "archiver", Endpoint.create("service", 127 << 24 | 1));
span = new Span.Builder()
    .traceId(1L)
    .name("targz")
    .id(1L)
    .timestamp(epochMicros())
    .duration(durationInMicros)
    .binaryAnnotations(archiver);

// Now, you can encode it as json or thrift
bytes = Codec.JSON.writeSpan(span);
bytes = Codec.THRIFT.writeSpan(span);
```

## Server
The [spring-boot server](https://github.com/openzipkin/zipkin-java/tree/master/zipkin-java-server) receives spans via HTTP POST and respond to queries from zipkin-web. It is a drop-in replacement for the [scala query service](https://github.com/openzipkin/zipkin/tree/master/zipkin-query-service), passing the same tests (via the interop module).

To run the server from the currently checked out source, enter the following.
```bash
$ ./mvnw -pl zipkin-java-server spring-boot:run
```

Note that the server requires minimum JRE 8.

## Artifacts
### Library Releases
Releases are uploaded to [Bintray](https://bintray.com/openzipkin/maven/zipkin-java).
### Library Snapshots
Snapshots are uploaded to [JFrog](http://oss.jfrog.org/artifactory/oss-snapshot-local) after commits to master.
### Docker Images
Released versions of zipkin-java-server are published to Docker Hub as `openzipkin/zipkin-java`.
See [docker-zipkin-java](https://github.com/openzipkin/docker-zipkin-java) for details.
