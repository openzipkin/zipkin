## zipkin-uiproxy Docker image

The `zipkin-uiproxy` testing image proxies the Zipkin UI with NGINX.

Besides norms defined in [docker-alpine](https://github.com/openzipkin/docker-alpine), this accepts the
following environment variables:

* `ZIPKIN_UI_BASEPATH`: The path this proxy serves the UI under. Defaults to /zipkin
* `ZIPKIN_BASE_URL`: The proxied zipkin base URL. Defaults to http://zipkin:9411

To build `openzipkin/zipkin-ui:test`, from the top-level of the repository, run:
```bash
$ DOCKER_FILE=docker/test-images/zipkin-uiproxy/Dockerfile build-bin/docker/docker_build openzipkin/zipkin-uiproxy:test
```
