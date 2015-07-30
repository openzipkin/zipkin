# zipkin-redis-example
The zipkin-redis-example project combines all parts of the service into a single
package pointed at a local redis server.

## Starting the example server
The following command will start an instance of ZipkinExample using the `redis-server`
you already have running.

```bash
./gradlew :zipkin-redis-example:run
```

You can customize execution by specifying your own `runArgs`. Use `-help` to discover what's available.

```bash
./gradlew :zipkin-redis-example:run -PrunArgs='-help'
```
## Using zipkin
Once started, you can browse traces in a web browser http://localhost:8080/
