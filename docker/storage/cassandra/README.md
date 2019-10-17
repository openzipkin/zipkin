## zipkin-cassandra Docker image

The `zipkin-cassandra` testing image contains Cassandra initialized with Zipkin's schema.

To build `openzipkin/zipkin-cassandra`, from the top level of the repository, run:

```bash
$ docker build -t openzipkin/zipkin-cassandra:test -f docker/storage/cassandra/Dockerfile .
```
