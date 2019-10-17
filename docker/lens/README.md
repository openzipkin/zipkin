## zipkin-ui Docker image

The `zipkin-ui` testing image contains the static parts of the Zipkin UI served directly with NGINX.

To build `openzipkin/zipkin-ui`, from the top level of the repository, run:

```bash
$ docker build -t openzipkin/zipkin-ui:test -f docker/Dockerfile --target zipkin-ui .
```
