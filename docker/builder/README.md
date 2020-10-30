# zipkin-builder

This image is for internal use only for building other Docker images. It refreshes a
cache of maven and npm dependencies so downstream builds do not have to do so every build.

This is rebuilt on master push, but can be republished manually like this:
```bash
# Build the builder and publish it
$ echo "$GH_TOKEN"| docker login ghcr.io -u "$GH_USER" --password-stdin
$ docker/build_image ghcr.io/openzipkin/zipkin-builder:latest push
```
