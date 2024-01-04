## zipkin-eureka Docker image

The `zipkin-eureka` testing image runs Eureka Server for service discovery
integration of the Zipkin server. This listens on port 8761.

To build `openzipkin/zipkin-eureka:test`, from the top-level of the repository, run:
```bash
$ DOCKER_FILE=docker/test-images/zipkin-eureka/Dockerfile build-bin/docker/docker_build openzipkin/zipkin-eureka:test
$ docker run -p 8761:8761 --rm openzipkin/zipkin-eureka:test
```

You can use the env variable `JAVA_OPTS` to change settings such as heap size for Eureka.
