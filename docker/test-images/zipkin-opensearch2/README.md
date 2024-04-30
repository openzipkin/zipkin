## zipkin-opensearch2 Docker image

The `zipkin-opensearch2` testing image runs OpenSearch 2.x for [Elasticsearch storage](../../../zipkin-storage/elasticsearch)
integration.

To build `openzipkin/zipkin-opensearch2:test`, from the top-level of the repository, run:
```bash
$ DOCKER_FILE=docker/test-images/zipkin-opensearch2/Dockerfile build-bin/docker/docker_build openzipkin/zipkin-opensearch2:test
```

You can use the env variable `OPENSEARCH_JAVA_OPTS` to change settings such as heap size for OpenSearch.

#### Host setup

OpenSearch is [strict](https://github.com/docker-library/docs/tree/master/elasticsearch#host-setup)
about virtual memory. You will need to adjust accordingly (especially if you notice OpenSearch crash!)

```bash
# If docker is running on your host machine, adjust the kernel setting directly
$ sudo sysctl -w vm.max_map_count=262144

# If using docker-machine/Docker Toolbox/Boot2Docker, remotely adjust the same
$ docker-machine ssh default "sudo sysctl -w vm.max_map_count=262144"

# If using colima, it is similar as well
$ colima ssh "sudo sysctl -w vm.max_map_count=262144"
```
