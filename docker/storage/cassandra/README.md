## zipkin-cassandra Docker image

A testing image containing Cassandra initialized with Zipkin's schema. To build, in the top level of
the repository, run something like

```bash
$ docker build -t openzipkin/zipkin-cassandra:test -f docker/storage/cassandra/Dockerfile .
```
