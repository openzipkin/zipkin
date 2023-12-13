## zipkin-activemq Docker image

The `zipkin-activemq` testing image runs ActiveMQ Classic for [ActiveMQ collector](../../../zipkin-collector/activemq)
integration.

To build `openzipkin/zipkin-activemq:test`, from the top-level of the repository, run:
```bash
$ DOCKER_FILE=docker/test-images/zipkin-activemq/Dockerfile build-bin/docker/docker_build openzipkin/zipkin-activemq:test
```

You can use the env variable `JAVA_OPTS` to change settings such as heap size for ActiveMQ.
