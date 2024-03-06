# Zipkin Docker Examples

This project is configured to run docker containers using
[docker-compose](https://docs.docker.com/compose/). Note that the default
configuration requires docker-compose 1.6.0+ and docker-engine 1.10.0+.

To start the default docker-compose configuration, run:

```bash
# To use the last released version of zipkin
$ docker-compose up
# To use the last built version of zipkin
$ TAG=master docker-compose up
```

View the web UI at $(docker ip):9411. Traces are stored in memory.

To see specific traces in the UI, select "zipkin-server" in the dropdown and
then click the "Find Traces" button.

## ActiveMQ

You can collect traces from [ActiveMQ](../test-images/zipkin-activemq/README.md) in addition to HTTP, using the
`docker-compose-activemq.yml` file. This configuration starts `zipkin` and `zipkin-activemq` in their
own containers.

To add ActiveMQ configuration, run:
```bash
$ docker-compose -f docker-compose-activemq.yml up
```

Then configure the [ActiveMQ sender](https://github.com/openzipkin/zipkin-reporter-java/blob/master/activemq-client/src/main/java/zipkin2/reporter/activemq/ActiveMQSender.java)
using a `brokerUrl` value of `failover:tcp://localhost:61616` or a non-local hostname if in docker.

## Cassandra

You can store traces in [Cassandra](../test-images/zipkin-cassandra/README.md) instead of memory, using the
`docker-compose-cassandra.yml` file. This configuration starts `zipkin`, `zipkin-cassandra` and
`zipkin-dependencies` (cron job) in their own containers.

To start the Cassandra-backed configuration, run:

```bash
$ docker-compose -f docker-compose-cassandra.yml up
```

The `zipkin-dependencies` container is a scheduled task that runs every hour.
If you want to see the dependency graph before then, you can run it manually
in another terminal like so:

```bash
$ docker-compose -f docker-compose-cassandra.yml run --rm --no-deps --entrypoint start-zipkin-dependencies dependencies
```

## Elasticsearch

You can store traces in [Elasticsearch](../test-images/zipkin-elasticsearch8/README.md) instead of memory,
using the `docker-compose-elasticsearch.yml` file. This configuration starts `zipkin`,
`zipkin-elasticsearch` and `zipkin-dependencies` (cron job) in their own containers.

To start the Elasticsearch-backed configuration, run:

```bash
$ docker-compose -f docker-compose-elasticsearch.yml up
```

The `zipkin-dependencies` container is a scheduled task that runs every hour.
If you want to see the dependency graph before then, you can run it manually
in another terminal like so:

```bash
$ docker-compose -f docker-compose-elasticsearch.yml run --rm --no-deps --entrypoint start-zipkin-dependencies dependencies
```

## Kafka

You can collect traces from [Kafka](../test-images/zipkin-kafka/README.md) in addition to HTTP, using the
`docker-compose-kafka.yml` file. This configuration starts `zipkin` and `zipkin-kafka` in their
own containers.

To add Kafka configuration, run:
```bash
$ docker-compose -f docker-compose-kafka.yml up
```

Then configure the [Kafka sender](https://github.com/openzipkin/zipkin-reporter-java/blob/master/kafka/src/main/java/zipkin2/reporter/kafka/KafkaSender.java) using a `bootstrapServers` value of `host.docker.internal:9092` if your application is inside the same docker network or `localhost:19092` if not, but running on the same host.

In other words, if you are running a sample application on your laptop, you would use `localhost:19092` bootstrap server to send spans to the Kafka broker running in Docker.

### Docker machine and Kafka

If you are using Docker machine, adjust `KAFKA_ADVERTISED_HOST_NAME` in `docker-compose-kafka.yml`
and the `bootstrapServers` configuration of the kafka sender to match your Docker host IP (ex. 192.168.99.100:19092).

## MySQL

You can store traces in [MySQL](../test-images/zipkin-mysql/README.md) instead of memory, using the
`docker-compose-mysql.yml` file. This configuration starts `zipkin`, `zipkin-mysql` and
`zipkin-dependencies` (cron job) in their own containers.

To start the MySQL-backed configuration, run:

```bash
$ docker-compose -f docker-compose-mysql.yml up
```

## RabbitMQ

You can collect traces from [RabbitMQ](../test-images/zipkin-rabbitmq/README.md) in addition to HTTP, using the
`docker-compose-rabbitmq.yml` file. This configuration starts `zipkin` and `zipkin-rabbitmq` in their
own containers.

To add RabbitMQ configuration, run:
```bash
$ docker-compose -f docker-compose-rabbitmq.yml up
```

Then configure the [RabbitMQ sender](https://github.com/openzipkin/zipkin-reporter-java/blob/master/amqp-client/src/main/java/zipkin2/reporter/amqp/RabbitMQSender.java)
using a `host` value of `localhost` or a non-local hostname if in docker.

## Eureka

You can register Zipkin for service discovery in [Eureka](../test-images/zipkin-eureka/README.md)
using the `docker-compose-eureka.yml` file. This configuration starts `zipkin` and `zipkin-eureka`
in their own containers.

When `zipkin` starts, it registers its endpoint into `eureka`. Then, the two [example services](#example)
discover zipkin's endpoint from `eureka` and use it to send spans.

To try this out, run:
```bash
$ docker-compose -f docker-compose.yml -f docker-compose-eureka.yml up
```

## Example

The docker-compose configuration can be extended to host an [example application](https://github.com/openzipkin/brave-example)
using the `docker-compose-example.yml` file. That file employs [docker-compose overrides](https://docs.docker.com/compose/extends/#multiple-compose-files)
to add a "frontend" and "backend" service.

To add the example configuration, run:
```bash
$ docker-compose -f docker-compose.yml -f docker-compose-example.yml up
```

Once the services start, open http://localhost:8081/
* This calls the backend (http://localhost:9000/api) and shows its result: a formatted date.

Afterward, you can view traces that went through the backend via http://localhost:9411/zipkin?serviceName=backend

## UI

The docker-compose configuration can be extended to [host the UI](../test-images/zipkin-ui/README.md) on port 80
using the `docker-compose-ui.yml` file. That file employs
[docker-compose overrides](https://docs.docker.com/compose/extends/#multiple-compose-files)
to add an NGINX container and relevant settings.

To start the NGINX configuration, run:

```bash
$ docker-compose -f docker-compose.yml -f docker-compose-ui.yml up
```

This container doubles as a skeleton for creating proxy configuration around
Zipkin like authentication, dealing with CORS with zipkin-js apps, or
terminating SSL.

If you want to run the zipkin-ui standalone against a remote zipkin server, you
need to set `ZIPKIN_BASE_URL` accordingly:

```bash
$ docker run -d -p 80:80 \
  -e ZIPKIN_BASE_URL=http://myfavoritezipkin:9411 \
  openzipkin/zipkin-ui
```

## UI Proxy

The docker-compose configuration can be extended to [proxy the UI](../test-images/zipkin-uiproxy/README.md) on port 80
using the `docker-compose-uiproxy.yml` file. That file employs
[docker-compose overrides](https://docs.docker.com/compose/extends/#multiple-compose-files) to add an NGINX container and relevant settings.

To start the NGINX configuration, run:

```bash
$ docker-compose -f docker-compose.yml -f docker-compose-uiproxy.yml up
```

This container helps verify the `ZIPKIN_UI_BASEPATH` variable by setting it to
"/admin/zipkin". This means when the compose configuration is up, you can
access Zipkin UI at http://localhost/admin/zipkin/

## Prometheus

Zipkin comes with a built-in Prometheus metric exporter. The
`docker-compose-prometheus.yml` file starts Prometheus configured to scrape
Zipkin, exposes it on port `9090`. You can open `$DOCKER_HOST_IP:9090` and
start exploring metrics (available on the `/prometheus` endpoint of Zipkin).

`docker-compose-prometheus.yml` also starts a Grafana with authentication
disabled, exposing it on port 3000. On startup it's configured with the
Prometheus instance started by `docker-compose` as a data source, and imports
the dashboard published at https://grafana.com/dashboards/1598. This means that,
after running `docker-compose  ... -f docker-compose-prometheus.yml up`, you
can open `$DOCKER_IP:3000/dashboard/db/zipkin-prometheus` and play around with
the dashboard.
