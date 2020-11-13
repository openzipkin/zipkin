## zipkin-mysql Docker image

The `zipkin-mysql` testing image runs MySQL 3.11.x initialized with Zipkin's schema for
[MySQL storage](../../../zipkin-storage/mysql-v1) integration.

To build `openzipkin/zipkin-mysql:test`, from the top-level of the repository, run:
```bash
$ DOCKER_FILE=docker/test-images/zipkin-mysql/Dockerfile build-bin/docker/docker_build openzipkin/zipkin-mysql:test
```

When running with docker-machine, you can connect like so:

```bash
$ mysql -h $(docker-machine ip) -u zipkin -pzipkin -D zipkin
```
