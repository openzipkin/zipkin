## zipkin Docker images
This directory contains assets used to build and release Zipkin's Docker images.

## Production images
The only Zipkin production images built here:
* openzipkin/zipkin: The core server image that hosts the Zipkin UI, Api and Collector features.
* openzipkin/zipkin-slim: The stripped server image that hosts the Zipkin UI and Api features, but only supports in-memory or Elasticsearch storage with HTTP or gRPC span collectors.

To build `openzipkin/zipkin`, from the top level of the repository, run:

```bash
$ docker build -t openzipkin/zipkin:test -f docker/Dockerfile .
```

If you want the slim distribution instead, run:

```bash
$ docker build -t openzipkin/zipkin-slim:test -f docker/Dockerfile . --target zipkin-slim
```

## Testing images

We also provide a number images that are not for production, rather to simplify demos and
integration tests. We designed these to be small and start easily. We did this by re-using the same
base layer `openzipkin/zipkin`, and setting up schema where relevant.

* [openzipkin/zipkin-cassandra](storage/cassandra/README.md) - runs Cassandra initialized with Zipkin's schema
* [openzipkin/zipkin-elasticsearch6](storage/elasticsearch6/README.md) - runs Elasticsearch 6.x
* [openzipkin/zipkin-elasticsearch7](storage/elasticsearch7/README.md) - runs Elasticsearch 7.x
* [openzipkin/zipkin-kafka](collector/kafka/README.md) - runs both Kafka+ZooKeeper
* [openzipkin/zipkin-mysql](storage/mysql/README.md) - runs MySQL initialized with Zipkin's schema
* [openzipkin/zipkin-ui](lens/README.md) - serves the (Lens) UI directly with NGINX

