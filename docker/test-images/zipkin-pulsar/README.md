## zipkin-pulsar Docker image

The `zipkin-pulsar` testing image runs Pulsar for Pulsar collector integration.

To build `openzipkin/zipkin-pulsar:test`, from the top-level of the repository, run:
```bash
$ DOCKER_FILE=docker/test-images/zipkin-pulsar/Dockerfile build-bin/docker/docker_build openzipkin/zipkin-pulsar:test
```

You can use the env variable `PULSAR_LOG_ROOT_LEVEL` to change the log level for Pulsar. Defaults to "WARN".