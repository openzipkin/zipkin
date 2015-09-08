# zipkin-java
Experimental java zipkin backend. Please look at issues as we are currently working on scope.


## Docker
You can run the experimental docker server to power the zipkin web UI. The following is a hack of the [docker-zipkin](https://github.com/openzipkin/docker-zipkin) docker-compose instructions.

```bash
# build ./zipkin-java-server/target/zipkin-server
$ ./mvnw clean install
# make a docker image out of it
$ docker build -t openzipkin/zipkin-java-server zipkin-java-server
# hook up the web ui
$ docker-compose up
```
