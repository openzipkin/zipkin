## zipkin-ui Docker image

The `zipkin-ui` testing image contains the static parts of the Zipkin UI served directly with NGINX.

To build `openzipkin/zipkin-ui:test`, from the top-level of the repository, run:
```bash
$ DOCKER_FILE=docker/test-images/zipkin-ui/Dockerfile build-bin/docker/docker_build openzipkin/zipkin-ui:test
```
