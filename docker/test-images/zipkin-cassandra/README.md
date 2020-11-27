## zipkin-cassandra Docker image

The `zipkin-cassandra` testing image runs Cassandra 3.11.x initialized with Zipkin's schema for
[Cassandra storage](../../../zipkin-storage/cassandra) integration.

Besides norms defined in [docker-java](https://github.com/openzipkin/docker-java), this accepts the
following environment variables:

 * `LOGGING_LEVEL`: Root Log4J logging level sent to stdout. Defaults to "WARN"

To build `openzipkin/zipkin-cassandra:test`, from the top-level of the repository, run:
```bash
$ DOCKER_FILE=docker/test-images/zipkin-cassandra/Dockerfile build-bin/docker/docker_build openzipkin/zipkin-cassandra:test
```
