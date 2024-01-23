## zipkin-eureka Docker image

The `zipkin-eureka` testing image runs Eureka Server for service discovery
integration of the Zipkin server. This listens on port 8761.

Besides norms defined in [docker-java](https://github.com/openzipkin/docker-java), this accepts the
following environment variables:

* `EUREKA_USERNAME`: username for authenticating endpoints under "/eureka".
* `EUREKA_PASSWORD`: password for authenticating endpoints under "/eureka".
* `JAVA_OPTS`: to change settings such as heap size for Eureka.

To build `openzipkin/zipkin-eureka:test`, from the top-level of the repository, run:
```bash
$ DOCKER_FILE=docker/test-images/zipkin-eureka/Dockerfile build-bin/docker/docker_build openzipkin/zipkin-eureka:test
$ docker run -p 8761:8761 --rm openzipkin/zipkin-eureka:test
```
