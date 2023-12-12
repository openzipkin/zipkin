## zipkin-rabbitmq Docker image

The `zipkin-rabbitmq` testing image runs `rabbitmq-server` for the
[RabbitMQ collector](../../../zipkin-collector/rabbitmq) integration.

For convenience, this includes the "guest" user and a default queue named
"zipkin". To add more queues, exec `amqp-declare-queue` in a running container.

To build `openzipkin/zipkin-rabbitmq:test`, from the top-level of the repository, run:
```bash
$ DOCKER_FILE=docker/test-images/zipkin-rabbitmq/Dockerfile build-bin/docker/docker_build openzipkin/zipkin-rabbitmq:test
```
