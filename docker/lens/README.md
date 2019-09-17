## zipkin-ui Docker image

To build a zipkin-ui Docker image, in the top level of the repository, run something
like

```bash
$ docker build -t openzipkin/zipkin-ui:test -f docker/Dockerfile .
```

### Dockerfile migration

We are currently migrating the Docker configuration from https://github.com/openzipkin/docker-zipkin/tree/master/zipkin-ui.
If making any changes here, make sure to also reflect them there.
