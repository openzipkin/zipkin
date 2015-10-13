# zipkin-java
Experimental java zipkin backend. Please look at issues as we are currently working on scope.


## Docker
You can run the experimental docker server to power the zipkin web UI. The following is a hack of the [docker-zipkin](https://github.com/openzipkin/docker-zipkin) docker-compose instructions.

```bash
# build zipkin-java-server/target/zipkin-java-server-0.1.0-SNAPSHOT.jar
$ ./mvnw clean install
# make a docker image out of it and hook up the web ui
$ docker-compose build
$ docker-compose up
```

## Release
### Snapshots
Snapshots are released to [Sonatype](https://oss.sonatype.org/#nexus-search;quick~zipkin-java) after commits to master are merged which are not pull requests.
