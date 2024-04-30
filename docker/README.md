## zipkin Docker images
This directory contains assets used to build and release Zipkin's Docker images.

## Production images
The only Zipkin production images built here:
* openzipkin/zipkin: The core server image that hosts the Zipkin UI, Api and Collector features.
  * Mirrored as ghcr.io/openzipkin/zipkin
* openzipkin/zipkin-slim: The stripped server image that hosts the Zipkin UI and Api features, but only supports in-memory or Elasticsearch storage with HTTP or gRPC span collectors.
  * Mirrored as ghcr.io/openzipkin/zipkin-slim

## Testing images

We also provide a number images that are not for production, rather to simplify demos and
integration tests. We designed these to be small and start easily. We did this by re-using the same
base layer `openzipkin/zipkin`, and setting up schema where relevant.

* [ghcr.io/openzipkin/zipkin-activemq](test-images/zipkin-activemq/README.md) - runs ActiveMQ Classic
* [ghcr.io/openzipkin/zipkin-cassandra](test-images/zipkin-cassandra/README.md) - runs Cassandra initialized with Zipkin's schema
* [ghcr.io/openzipkin/zipkin-elasticsearch7](test-images/zipkin-elasticsearch7/README.md) - runs Elasticsearch 7.x
* [ghcr.io/openzipkin/zipkin-elasticsearch8](test-images/zipkin-elasticsearch8/README.md) - runs Elasticsearch 8.x
* [ghcr.io/openzipkin/zipkin-opensearch2](test-images/zipkin-opensearch2/README.md) - runs OpenSearch 2.x
* [ghcr.io/openzipkin/zipkin-eureka](test-images/zipkin-eureka/README.md) - runs Eureka
* [ghcr.io/openzipkin/zipkin-kafka](test-images/zipkin-kafka/README.md) - runs both Kafka+ZooKeeper
* [ghcr.io/openzipkin/zipkin-mysql](test-images/zipkin-mysql/README.md) - runs MySQL initialized with Zipkin's schema
* [ghcr.io/openzipkin/zipkin-rabbitmq](test-images/zipkin-rabbitmq/README.md) - runs RabbitMQ
* [ghcr.io/openzipkin/zipkin-ui](test-images/zipkin-ui/README.md) - serves the (Lens) UI directly with NGINX

## Getting started

Zipkin has no dependencies, for example you can run an in-memory zipkin server like so:

```bash
# Note: this is mirrored as ghcr.io/openzipkin/zipkin-slim
$ docker run -d -p 9411:9411 openzipkin/zipkin-slim
```

See the ui at (docker ip):9411

In the UI - click zipkin-server, then click "Find Traces".

We also provide [example compose files](examples/README.md) that integrate collectors and storage,
such as Kafka or Elasticsearch.

## Configuration
Configuration is via environment variables, defined by [zipkin-server](../zipkin-server/README.md). Notably, you'll want to look at the `STORAGE_TYPE` environment variables, which
include "cassandra", "mysql" and "elasticsearch".

Note: the `openzipkin/zipkin-slim` image only supports "elasticsearch" storage. To use other storage types, you must use the main image `openzipkin/zipkin`.

When in Docker, the following environment variables also apply

* `JAVA_OPTS`: Use to set java arguments, such as heap size or trust store location.
  * By default, `openzipkin/zipkin` sets max heap to 64m while `openzipkin/zipkin-slim` 32m
* `STORAGE_PORT_9042_TCP_ADDR` -- A Cassandra node listening on port 9042. This
  environment variable is typically set by linking a container running
  `zipkin-cassandra` as "storage" when you start the container.
* `STORAGE_PORT_3306_TCP_ADDR` -- A MySQL node listening on port 3306. This
  environment variable is typically set by linking a container running
  `zipkin-mysql` as "storage" when you start the container.
* `STORAGE_PORT_9200_TCP_ADDR` -- An Elasticsearch node listening on port 9200. This
  environment variable is typically set by linking a container running
  `zipkin-elasticsearch` as "storage" when you start the container. This is ignored
  when `ES_HOSTS` or `ES_AWS_DOMAIN` are set.
* `KAFKA_PORT_2181_TCP_ADDR` -- A zookeeper node listening on port 2181. This
  environment variable is typically set by linking a container running
  `zipkin-kafka` as "kafka" when you start the container.

For example, to increase heap size, set `JAVA_OPTS` as shown in our [docker-compose](examples/docker-compose.yml) file:
```yaml
    environment:
      - JAVA_OPTS=-Xms128m -Xmx128m -XX:+ExitOnOutOfMemoryError
```

For example, to add debug logging, set `command` as shown in our [docker-compose](examples/docker-compose.yml) file:
```yaml
    command: --logging.level.zipkin2=DEBUG
```

## Runtime user
The `openzipkin/zipkin` and `openzipkin/zipkin-slim` images run under a nologin
user named 'zipkin' with a home directory of '/zipkin'. As this is Alpine Linux
image, you won't find many utilities installed, but you can browse contents
with a shell like below:

```bash
$ docker run -it --rm --entrypoint /bin/sh openzipkin/zipkin
/zipkin $ ls
BOOT-INF  META-INF  org       run.sh
```

## Notes

### Container links
If using Docker's deprecated container links, you need to set env variables
accordingly.

Ex. If your link name is "storage" for an Elasticsearch container:
```
  ES_HOSTS=http://$STORAGE_PORT_9200_TCP_ADDR:9200
```

The above is mentioned only for historical reasons. The OpenZipkin community
do not support Docker's deprecated container links.

### MySQL
If using an external MySQL server or image, ensure schema and other parameters match the [docs](../zipkin-storage/mysql-v1/README.md#applying-the-schema).

## Building images

To build `openzipkin/zipkin:test`, from the top-level of the repository, run:
```bash
$ build-bin/docker/docker_build openzipkin/zipkin:test
```

If you want the slim distribution (openzipkin/zipkin-slim:test), run:
```bash
$ DOCKER_TARGET=zipkin-slim build-bin/docker/docker_build openzipkin/zipkin-slim:test
```
