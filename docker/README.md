## zipkin-server Docker image

To build a zipkin-server Docker image, in the top level of the repository, run something
like

```bash
$ docker build -t openzipkin/zipkin:test -f docker/Dockerfile .
```

If you want the slim distribution instead, run something like

```bash
$ docker build -t openzipkin/zipkin:test -f docker/Dockerfile . --target zipkin-server-slim
```

## zipkin-ui Docker image

We also provide an image that only contains the static parts of the Zipkin UI served directly with
nginx. To build, run something like

```bash
$ docker build -t openzipkin/zipkin-ui:test -f docker/Dockerfile --target zipkin-ui .
```

### Dockerfile migration

We are currently migrating the Docker configuration from https://github.com/openzipkin/docker-zipkin/tree/master/zipkin.
If making any changes here, make sure to also reflect them there.
