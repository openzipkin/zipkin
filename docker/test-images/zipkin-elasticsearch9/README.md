## zipkin-elasticsearch9 Docker image

The `zipkin-elasticsearch9` testing image runs Elasticsearch 9.x for [Elasticsearch storage](../../../zipkin-storage/elasticsearch)
integration.

To build `openzipkin/zipkin-elasticsearch9:test`, from the top-level of the repository, run:
```bash
$ DOCKER_FILE=docker/test-images/zipkin-elasticsearch9/Dockerfile build-bin/docker/docker_build openzipkin/zipkin-elasticsearch9:test
```

You can use the env variable `ES_JAVA_OPTS` to change settings such as heap size for Elasticsearch.

#### Host setup

Elasticsearch is [strict](https://github.com/docker-library/docs/tree/master/elasticsearch#host-setup)
about virtual memory. You will need to adjust accordingly (especially if you notice Elasticsearch crash!)

```bash
# If docker is running on your host machine, adjust the kernel setting directly
$ sudo sysctl -w vm.max_map_count=262144

# If using docker-machine/Docker Toolbox/Boot2Docker, remotely adjust the same
$ docker-machine ssh default "sudo sysctl -w vm.max_map_count=262144"

# If using colima, it is similar as well
$ colima ssh "sudo sysctl -w vm.max_map_count=262144"
```

#### License

This Elasticsearch image is only made for testing features supported by Zipkin,
and is subject to [Elastic-License-2.0](https://www.elastic.co/licensing/elastic-license).
For more details, inspect the LICENSE.txt and NOTICE.txt in the /elasticsearch
directory of this image.
