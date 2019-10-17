## zipkin-server Docker image

To build a zipkin-server Docker image, in the top level of the repository, run something
like

```bash
$ docker build -t openzipkin/zipkin:test -f docker/Dockerfile .
```

If you want the slim distribution instead, run something like

```bash
$ docker build -t openzipkin/zipkin:test -f docker/Dockerfile . --target zipkin-slim
```

## zipkin-ui Docker image

We also provide an image that only contains the static parts of the Zipkin UI served directly with
nginx. To build, run something like

```bash
$ docker build -t openzipkin/zipkin-ui:test -f docker/Dockerfile --target zipkin-ui .
```

## zipkin-kafka Docker image

We also provide an image that runs both Kafka+ZooKeeper for testing the [Kafka collector](https://github.com/openzipkin/zipkin/tree/master/zipkin-collector/kafka)
and the upcoming [Kafka storage](https://github.com/openzipkin-contrib/zipkin-storage-kafka).
To build, run something like

```bash
$ docker build -t openzipkin/zipkin-kafka:test -f docker/kafka/Dockerfile .
```

Then configure the [Kafka sender](https://github.com/openzipkin/zipkin-reporter-java/blob/master/kafka/src/main/java/zipkin2/reporter/kafka/KafkaSender.java) using a `bootstrapServers` value of `host.docker.internal:9092` if your application is inside the same docker network or `localhost:19092` if not, but running on the same host.

In other words, if you are running a sample application on your laptop, you would use `localhost:19092` bootstrap server to send spans to the Kafka broker running in Docker.

### Dockerfile migration

We are currently migrating the Docker configuration from https://github.com/openzipkin/docker-zipkin/tree/master/zipkin.
If making any changes here, make sure to also reflect them there.
